from fastapi.testclient import TestClient

from app.main import app


class FakeDatabase:
    async def ping(self) -> bool:
        return True

    def close(self) -> None:
        pass


class FakeRag:
    async def check(self) -> bool:
        return True


def test_health_endpoint_reports_dependency_status() -> None:
    with TestClient(app) as client:
        app.state.database = FakeDatabase()
        app.state.rag = FakeRag()
        response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_query_request_requires_non_empty_question() -> None:
    with TestClient(app) as client:
        response = client.post("/api/query", json={"query": "", "session_id": "session"})
    assert response.status_code == 422
