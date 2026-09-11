# AI

AI integration is provider-neutral. `AiProvider` defines workflow generation, explanation and validation contracts. Provider adapters can target OpenAI-compatible, Gemini-compatible, OpenRouter-compatible and custom endpoints.

The intended lifecycle is Generate → Validate → Preview → User approval → Create/Update. Credentials and high-impact actions are never silently executed from generated output.
