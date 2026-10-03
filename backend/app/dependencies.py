from fastapi import Request


def get_database(request: Request):
    return request.app.state.database


def get_rag_service(request: Request):
    return request.app.state.rag


def get_llm_service(request: Request):
    return request.app.state.llm
