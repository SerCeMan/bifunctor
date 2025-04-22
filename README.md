# BiFunctor (■,■)

![Build](https://github.com/SerCeMan/bifunctor/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
BiFunctor is a hackable agentic coding assistant for IntelliJ Platform IDEs.

## Current Status

The plugin is in the early stages of development. You are welcome to try it out, but please be aware that not everything
might work as expected.

<!-- Plugin description end -->

## Getting started

Please note that only IntelliJ 2025.1+ is supported. After installing the plugin, configure at least one LLM provider in
the settings. Then, open the BiFunctor tool window by clicking the icon on the right side of the editor, and hack away!

## Why not X?

### Junie

There are already a number of agentic assistant plugins available for IntelliJ Platform IDEs, for example JetBrains'
Junie, or Windsurf. However, being closed source, they limit your ability to fit them to match your codebase and your
workflow.

### Aider and other cli tools

The key to achieving optimal results with LLMs is providing them with high-quality context. JetBrains IDEs excel at this
by indexing the entire codebase, resolving references, performing efficient searches, and exposing the same APIs and
tools to the agent that are available to you as a developer working within the IDE.

## Reporting an issue

Please attach a screen recording for every issue if possible. It will make the issue much clearer and easier to
understand.

## Development

```bash
# building the plugin distribution
./gradlew buildPlugin
# running the plugin in the IDE
./gradlew runIde
```

## Contribute

Contributions are always welcome!
