// Root project is a settings-only aggregator. All code lives in the modules:
//   :core-ui          authoring runtime, modifier extensions, reusable components
//   :feature-profile  the Profile screen
//   :feature-samples  demo / showcase screens
//   :server           runnable app — HTTP server, JSON DocumentBuilder, registry
// Plugin versions are declared here (apply false) so modules apply them without a version.
plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
}
