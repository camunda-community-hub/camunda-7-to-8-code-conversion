> [!IMPORTANT]
> **This project has moved!**
>
> The **Camunda 7 to 8 Code Conversion** tooling has been integrated into the official [camunda-7-to-8-migration-tooling](https://github.com/camunda/camunda-7-to-8-migration-tooling) repository.
>
> **What this means for you:**
> - ✅ **Official support**: Now officially maintained by Camunda, ensuring long-term stability and updates.
>
> **Please visit the new repository for:**
> - Latest source code and documentation
> - Issue reporting and feature requests
> - Contributing to the codebase
>
> This repository is now archived.

# Camunda 7 to 8 Code Conversion

As Camunda 8 is a complete rewrite of Camunda 7, you must adjust solutions developed for Camunda 7 to run on Camunda 8.

This project:

-   Describes **[code conversion patterns (WIP)](patterns/)**, specifically for **client code** and **glue code** of Java (Spring) solutions.

-   Provides **[OpenRewrite recipes (WIP)](recipes/)** to automate refactoring of Java solutions.

-   Hosts a **[Camunda 7 API to Camunda 8 API Mapping WebApp (WIP)](https://camunda-community-hub.github.io/camunda-7-to-8-code-conversion/)** to quickly find language-agnostic information about Camunda 7 API endpoints and how they relate to Camunda 8 endpoints. Specifically:
    -   Which Camunda 7 endpoints map directly to Camunda 8 endpoints and how
    -   Which Camunda 7 endpoints are on the roadmap for upcoming Camunda 8 releases
    -   Which Camunda 7 endpoints are handled conceptually differently in Camunda 8
    -   Which Camunda 7 endpoints are discontinued

See also **[Camunda 7 to 8 Migration Example in Action](https://github.com/camunda-community-hub/camunda-7-to-8-migration-example)**
