"""
LangGraph 状态图构建。
                  START
                    │
                intent_node
                    │
        ┌───────────┼───────────┐
        ▼           ▼           ▼
     tool_node   rag_node   reply_node(chitchat/transfer)
        │           │
        └─────┬─────┘
              ▼
          reply_node
              │
          router_node
        ┌─────┴─────┐
       END      END(need_human)
"""
from langgraph.graph import StateGraph, END

from graph.state import CustomerServiceState
from graph.nodes import intent_node, tool_node, rag_node, reply_node, router_node


def route_by_intent(state: dict) -> str:
    mapping = {
        "order_query": "tool",
        "order_modify": "tool",
        "order_cancel": "tool",
        "refund": "tool",
        "product_query": "tool",
        "account_query": "tool",
        "faq": "rag",
        "chitchat": "reply",
        "transfer_human": "reply",
    }
    return mapping.get(state.get("intent"), "reply")


def build_graph():
    workflow = StateGraph(CustomerServiceState)

    workflow.add_node("intent", intent_node)
    workflow.add_node("tool", tool_node)
    workflow.add_node("rag", rag_node)
    workflow.add_node("reply", reply_node)

    workflow.set_entry_point("intent")
    workflow.add_conditional_edges("intent", route_by_intent)
    workflow.add_edge("tool", "reply")
    workflow.add_edge("rag", "reply")
    workflow.add_conditional_edges(
        "reply",
        router_node,
        {"end": END, "transfer_human": END},
    )

    return workflow.compile()


# 编译后的图实例（模块加载即构建）
graph = build_graph()