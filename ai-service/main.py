from fastapi import FastAPI, UploadFile, File, Form

from ai_service import analyze_image


app = FastAPI(title="SatQuery AI Service")


@app.get("/")
def root():
    return {
        "service": "SatQuery AI Service",
        "status": "running"
    }


@app.post("/analyze")
async def analyze(
    query: str = Form(...),
    image1: UploadFile = File(...),
    image2: UploadFile | None = File(None)
):

    image1_data = await image1.read()

    image2_data = None

    if image2:
        image2_data = await image2.read()

    result = await analyze_image(
        query=query,
        image1=image1_data,
        image2=image2_data,
        image1_mime_type=image1.content_type or "image/jpeg",
        image2_mime_type=image2.content_type if image2 else "image/jpeg"
    )

    return result