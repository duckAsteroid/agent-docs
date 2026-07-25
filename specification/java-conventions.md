# Java Conventions

The standard for sharing Java libraries is the JAR file. 

Agent docs for JAR files are published in one of two ways:
1. Embedded inside the JAR itself as a resource
2. Shared as a sidecar artifact that is published alongside the JAR (i.e. similar to how source.zip and JavaDocs are 
   published)  

To indicate that your JAR has agent docs you simply include an `Agent-Docs` entry in the standard manifest `META-INF/MANIFEST.MF`.

The following values for that are interpreted by consumers to resolve your agent docs...

| Value                                                                       | Meaning                                                                                                                                                                                                                                               |
|-----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| *ABSENT* <br> i.e. no `Agent-Docs` entry or an empty or unrecognised value. | This artifact has no agent docs.                                                                                                                                                                                                                      |
| `Agent-Docs: classpath`                                                     | Docs are embedded in this same JAR's resources at the conventional default path, `agent-docs/`.                                                                                                                                                       |
| `Agent-Docs: classpath:<path>`                                              | Docs are embedded at a custom path instead of the default. `<path>` is interpreted as a regular classpath resource relative to this JAR root.                                                                                                         |
| `Agent-Docs: maven`                                                         | A sidecar zip is published at this artifact's own coordinates. |
| `Agent-Docs: maven:<group>:<artifact>:<version>`                            | A sidecar zip is published at the given, explicitly-declared coordinates.                                                                                                                                                                             |

## Sidecar Distribution

When agent docs are distributed via maven they are often packaged as a separate artifact, classified `agent-docs`. The 
artifact is just a regular ZIP file containing the agent docs bundle. This avoids excessive agent docs "bloat" in the 
core JAR file. 

## Gradle Tooling

If you are using Gradle for your project (either as a consumer or publisher of agent docs). Some gradle plugins are
available that implement this specification in an opinionated fashion:

* [`io.github.duckasteroid.agent-docs`](../agent-docs-resolve-gradle-plugin/README.md) - a plugin to help resolve agent docs for your dependencies
* [`io.github.duckasteroid.agent-docs.publish`](../agent-docs-publish-gradle-plugin/README.md) - a plugin to help publish agent docs for your library