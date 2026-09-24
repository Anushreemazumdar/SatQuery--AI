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
You are SatQuery AI, an AI assistant for satellite imagery analysis.

The user may provide:
- Sentinel-1 SAR imagery
- Sentinel-2 optical imagery
- One image
- Two images representing different dates

Your job is to analyze the provided imagery according to the user's
natural-language query.

Important rules:

1. Do not claim scientific certainty.
2. Clearly distinguish observations from assumptions.
3. If two images are provided, compare them and describe visible changes.
4. If only one image is provided, analyze only that image.
5. If the image appears to be SAR imagery, remember that it is radar data
   and may look very different from optical imagery.
6. If the image type cannot be determined, say so.
7. Do not invent coordinates, measurements, dates, or objects that cannot
   reasonably be inferred from the image.
8. Return concise but useful explanations.
9. Give a confidence value between 0 and 1 based on visual clarity and
   certainty of the interpretation.
10. For visual evidence, provide approximate bounding boxes when possible.
    Bounding-box coordinates must be normalized from 0 to 1000:
      x = left position
      y = top position
      width = box width
      height = box height

The output MUST be valid JSON.
"""


def build_prompt(query: str, has_second_image: bool) -> str:

    image_context = (
        "Two images have been provided. Treat them as a before/after "
        "or comparison pair when appropriate."
        if has_second_image
        else
        "Only one image has been provided."
    )

    return f"""
{SYSTEM_PROMPT}

Image context:
{image_context}

User query:
{query}

Return JSON in exactly this structure:

{{
  "answer": "A concise natural-language answer to the user's question.",
  "confidence": 0.0,
  "task": "VQA | CHANGE_ANALYSIS | CAPTIONING | OBJECT_DETECTION | GENERAL_ANALYSIS",
  "model": "{MODEL_NAME}",
  "explanation": "A short explanation of the visual evidence.",
  "evidence": [
    {{
      "label": "description of observed region",
      "x": 0,
      "y": 0,
      "width": 0,
      "height": 0
    }}
  ]
}}

If there are no reliable regions to highlight, return:

"evidence": []

Do not put markdown fences around the JSON.
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

        # First image
        contents.append(
            types.Part.from_bytes(
                data=image1,
                mime_type=image1_mime_type
            )
        )

        # Second image, if supplied
        if image2:
            contents.append(
                types.Part.from_bytes(
                    data=image2,
                    mime_type=image2_mime_type
                )
            )

        # Add the analysis prompt after the images
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

        raw_text = response.text

        if not raw_text:
            raise RuntimeError("Gemini returned an empty response.")

        try:
            result = json.loads(raw_text)

        except json.JSONDecodeError:

            # Fallback if the model unexpectedly returns non-JSON text
            result = {
                "answer": raw_text,
                "confidence": 0.5,
                "task": "GENERAL_ANALYSIS",
                "model": MODEL_NAME,
                "explanation": "The model returned an unstructured response.",
                "evidence": []
            }

        # Ensure important fields exist
        result.setdefault("answer", "")
        result.setdefault("confidence", 0.5)
        result.setdefault("task", "GENERAL_ANALYSIS")
        result.setdefault("model", MODEL_NAME)
        result.setdefault("explanation", "")
        result.setdefault("evidence", [])

        return result

    # except Exception as e:

    #     return {
    #         "answer": "AI analysis could not be completed.",
    #         "confidence": 0.0,
    #         "task": "ERROR",
    #         "model": MODEL_NAME,
    #         "explanation": str(e),
    #         "evidence": []
    #     }
    # 
    except Exception as ex:
        print("GEMINI ERROR:", repr(ex))
        return {
            "answer": "AI analysis could not be completed.",
            "confidence": 0.0,
            "task": "ERROR",
            "model": MODEL_NAME,
            "explanation": str(ex),
            "evidence": []
        }