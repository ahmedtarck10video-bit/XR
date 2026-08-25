pluginManagement {
  // existing repositories...
  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == "com.google.devtools.ksp") {
        useVersion("2.2.10-2.0.2") // force the version you want
      }
    }
  }
}
