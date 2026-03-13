import os
import tempfile
from pathlib import Path

from fastapi import FastAPI, UploadFile, HTTPException
from pydantic import BaseModel
from docling.document_converter import DocumentConverter

app = FastAPI(title="THOT Document Parser")

# Initialise Docling une seule fois (chargement des modeles)
converter = DocumentConverter()

MAX_FILE_SIZE = 15 * 1024 * 1024  # 15 MB


class ParseResponse(BaseModel):
    filename: str
    content_type: str
    page_count: int | None = None
    char_count: int
    extracted_text: str


@app.post("/parse", response_model=ParseResponse)
async def parse(file: UploadFile):
    if not file.filename:
        raise HTTPException(status_code=400, detail="No filename provided")

    # Lire le contenu et verifier la taille
    content = await file.read()
    if len(content) > MAX_FILE_SIZE:
        raise HTTPException(status_code=413, detail="File too large (max 10MB)")

    # Sauvegarder dans un fichier temporaire (Docling a besoin d'un path)
    suffix = Path(file.filename).suffix
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            tmp.write(content)
            tmp_path = tmp.name

        # Conversion via Docling
        result = converter.convert(tmp_path)
        extracted_text = result.document.export_to_markdown()

        # Compter les pages si disponible
        page_count = None
        if hasattr(result.document, "pages") and result.document.pages:
            page_count = len(result.document.pages)

        return ParseResponse(
            filename=file.filename,
            content_type=file.content_type or "application/octet-stream",
            page_count=page_count,
            char_count=len(extracted_text),
            extracted_text=extracted_text,
        )

    except Exception as e:
        raise HTTPException(status_code=422, detail=f"Failed to parse document: {str(e)}")

    finally:
        # Nettoyer le fichier temporaire
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


@app.get("/health")
def health():
    return {"status": "ok"}
