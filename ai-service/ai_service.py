import os
import json
from typing import Optional

from dotenv import load_dotenv
from google import genai
from google.genai import types


load_dotenv()

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

if not GEMINI_API_KEY:
    raise RuntimeError(
        "GEMINI_API_KEY is not set. Add it to your .env file."
    )


client = genai.Client(api_key=GEMINI_API_KEY)

MODEL_NAME = "gemini-3.5-flash-lite"



SYSTEM_PROMPT = """
You are SatQuery AI, an assistant specialized in satellite imagery analysis
and geospatial visual interpretation.

Analyze ONLY what can reasonably be inferred from the provided image(s) and
the user's query.

GENERAL RULES
1. Do not invent coordinates, locations, dates, sensor metadata, distances,
   areas, measurements, object identities, or environmental conditions.
2. Do not claim scientific certainty.
3. Clearly distinguish direct visual observations from interpretations.
4. If image type can be determined, describe it as Sentinel-1 SAR,
   Sentinel-2 optical, another optical image, another radar image, or unknown.
5. If image type cannot be determined reliably, say so.
6. With one image, analyze only that image and do not claim temporal change.
7. With two images, compare visible differences and mention possible
   alternative explanations such as illumination, acquisition conditions,
   sensor differences, seasonality, or image quality.
8. Interpret SAR imagery using radar backscatter, texture, geometry, shadows,
   and other radar characteristics rather than treating it like ordinary
   optical imagery.
9. For optical imagery, consider visible vegetation, water, built-up areas,
   roads, exposed soil, clouds, shadows, and clearly visible structures.
10. Use cautious language such as "appears to be", "possibly",
    "consistent with", or "may indicate" when interpretation is uncertain.
11. Confidence must be a number between 0 and 1 and must reflect image
    quality, visual clarity, consistency, and ambiguity. It is not a
    probability that an interpretation is objectively true.
12. Bounding boxes are optional. If used, coordinates must be normalized
    from 0 to 1000:
       x = left, y = top, width = box width, height = box height.
    Do not fabricate precise boxes.
13. For two images, possible change categories include vegetation change,
    water extent change, built-up change, infrastructure change,
    land-surface change, possible flooding, possible construction or
    demolition, possible disturbance, and other visible change.
14. Answer the user's question directly. If it cannot be determined from
    the imagery, say so instead of guessing.

OUTPUT CONTRACT
Return ONLY valid JSON using EXACTLY this structure:

{
  "answer": "A concise natural-language answer based only on the provided imagery and user query.",
  "confidence": 0.0,
  "task": "VQA",
  "model": "MODEL_NAME",
  "explanation": "A short explanation of the visual evidence.",
  "evidence": [
    {
      "label": "description of an observed region",
      "x": 0,
      "y": 0,
      "width": 0,
      "height": 0
    }
  ]
}

OUTPUT RULES
- Return JSON only. No Markdown and no code fences.
- Always include all six fields: answer, confidence, task, model,
  explanation, evidence.
- confidence must be between 0 and 1.
- evidence must always be an array. Use [] when there is no reliable region.
- task must be one of:
  "VQA", "CHANGE_DETECTION", "CAPTIONING", "GROUNDING",
  "OPTICAL_SAR", "GENERAL_ANALYSIS".
- For two images used for comparison, use "CHANGE_DETECTION".
- For a single image, do not report temporal change.
- Never invent evidence, coordinates, measurements, dates, or metadata.
- Keep answer and explanation concise enough for an interactive prototype.
"""



def build_prompt(query: str, has_second_image: bool) -> str:
    image_context = (
        "Two images have been provided. Compare them as a before/after pair "
        "when the query requires change detection."
        if has_second_image
        else
        "Only one image has been provided. Do not perform temporal change detection."
    )

    return f"""
{SYSTEM_PROMPT}

IMAGE CONTEXT:
{image_context}

USER QUERY:
{query}

IMPORTANT:
Return exactly one JSON object matching the OUTPUT CONTRACT above.
Do not add any text before or after the JSON.
"""


async def analyze_image(
    query: str,
    image1: bytes,
    image2: Optional[bytes] = None,
    image1_mime_type: str = "image/jpeg",
    image2_mime_type: str = "image/jpeg"
):
    try:
        contents = []

        contents.append(
            types.Part.from_bytes(
                data=image1,
                mime_type=image1_mime_type
            )
        )

        if image2:
            contents.append(
                types.Part.from_bytes(
                    data=image2,
                    mime_type=image2_mime_type
                )
            )

        contents.append(
            build_prompt(
                query=query,
                has_second_image=image2 is not None
            )
        )

        response = client.models.generate_content(
            model=MODEL_NAME,
            contents=contents,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                temperature=0.2
            )
        )

        raw_text = (response.text or "").strip()

        if not raw_text:
            raise RuntimeError("Gemini returned an empty response.")

        try:
            result = json.loads(raw_text)
        except json.JSONDecodeError as ex:
            raise RuntimeError(
                f"Gemini returned invalid JSON: {raw_text[:500]}"
            ) from ex

        if not isinstance(result, dict):
            raise RuntimeError("Gemini returned JSON, but it was not an object.")

        answer = result.get("answer")
        if not isinstance(answer, str) or not answer.strip():
            raise RuntimeError("Gemini JSON did not contain a valid 'answer'.")

        try:
            confidence = float(result.get("confidence", 0.5))
        except (TypeError, ValueError):
            confidence = 0.5

        confidence = max(0.0, min(1.0, confidence))

        allowed_tasks = {
            "VQA",
            "CHANGE_DETECTION",
            "CAPTIONING",
            "GROUNDING",
            "OPTICAL_SAR",
            "GENERAL_ANALYSIS",
        }

        task = result.get("task", "GENERAL_ANALYSIS")
        if task not in allowed_tasks:
            task = "CHANGE_DETECTION" if image2 is not None else "GENERAL_ANALYSIS"

        evidence = result.get("evidence", [])
        if not isinstance(evidence, list):
            evidence = []

        return {
            "answer": answer.strip(),
            "confidence": confidence,
            "task": task,
            "model": result.get("model") or MODEL_NAME,
            "explanation": str(result.get("explanation") or ""),
            "evidence": evidence,
        }

    except Exception as ex:
        print("========== GEMINI ERROR ==========")
        print(repr(ex))
        print("==================================")

        return {
            "answer": "AI analysis could not be completed. Please try again.",
            "confidence": 0.0,
            "task": "CHANGE_DETECTION" if image2 is not None else "GENERAL_ANALYSIS",
            "model": MODEL_NAME,
            "explanation": str(ex),
            "evidence": []
        }

