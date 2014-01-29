// Comment to get more information during initialization
logLevel := Level.Warn

resolvers += "typesafe releases" at "http://repo.typesafe.com/typesafe/releases"

// Use the Play sbt plugin for Play projects
addSbtPlugin("play" % "sbt-plugin" % "2.1.5")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.6.4")
