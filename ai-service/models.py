from pydantic import BaseModel
from typing import Optional


class AIResponse(BaseModel):
    answer: str
    confidence: float
    task: str
    model: str
    explanation: Optional[str] = None