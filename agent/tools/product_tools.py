"""商品相关工具：查询商品详情、搜索商品。"""
from langchain_core.tools import tool

from tools.java_client import call_java


@tool
async def query_product(product_id: str) -> dict:
    """查询商品详情。输入商品ID，返回商品名称、价格、描述、销量等。"""
    return await call_java("queryProduct", {"productId": product_id})


@tool
async def search_product(keyword: str) -> dict:
    """搜索商品。输入关键词，返回匹配的商品列表（含商品ID、名称、价格）。"""
    return await call_java("searchProduct", {"keyword": keyword})