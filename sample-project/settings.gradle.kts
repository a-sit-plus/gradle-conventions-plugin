
rootProject.name = "sample-project"

//We want to stick to the legacy plugin
System.setProperty("at.asitplus.gradle","legacy")
includeBuild("..")

include("sample-module")
