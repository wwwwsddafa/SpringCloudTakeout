"""用户相关工具：查询用户基本信息。"""
from langchain_core.tools import tool

from tools.java_client import call_java


@tool
async def query_user_info() -> dict:
    """查询当前用户的基本信息。无需参数，自动获取当前用户的用户名、手机号、会员等级、账户余额等。"""
    return await call_java("queryUserInfo", {})