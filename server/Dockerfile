FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /srv

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY story_server ./story_server

RUN useradd --system --uid 10001 story && mkdir /data && chown story /data
USER story
VOLUME /data

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/healthz')"
CMD ["uvicorn", "story_server.main:app", "--factory", "--host", "0.0.0.0", "--port", "8080", "--proxy-headers"]
