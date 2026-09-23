# AlterLingua backend image. Build from the backend/ folder:  docker build -f deploy/Dockerfile -t alterlingua-api .
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# Run as an ordinary user: the server needs no special rights.
RUN useradd --system --no-create-home alterlingua
USER alterlingua

EXPOSE 8000
# --proxy-headers: the real client address arrives from the reverse proxy in front of it.
CMD ["uvicorn", "app.main:app_factory", "--factory", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--forwarded-allow-ips", "*"]
