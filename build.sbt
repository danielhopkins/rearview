import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.debian.Keys.{name => _, version => _, _}
import com.typesafe.sbt.packager.linux.LinuxPackageMapping

name := "rearview"

version := "2.1.0"

scalaVersion := "2.11.4"

maintainer in Debian:= "Jeff Simpson <jeff@victorops.com>"

packageSummary := "Monitoring framework for Graphite metrics"

packageDescription in Debian:= "Monitoring framework for Graphite metrics"

parallelExecution in Test := false

unmanagedResourceDirectories in Assets += baseDirectory.value / "public2"

unmanagedResourceDirectories in Compile += new File("src/main/resources")

unmanagedSourceDirectories in Compile += new File("src/main/resources")

libraryDependencies ++= Seq(
  "commons-io"                    %  "commons-io"                  % "2.4",
  "com.typesafe.slick"            %% "slick"                       % "2.1.0",
  "com.typesafe.akka"             %% "akka-agent"                  % "2.3.8",
  "com.typesafe.akka"             %% "akka-cluster"                % "2.3.8",
  "com.typesafe.play.plugins"     %% "play-statsd"                 % "2.3.0",
  "commons-validator"             %  "commons-validator"           % "1.4.0",
  "javolution"                    %  "javolution"                  % "5.5.1",
  "mysql"                         %  "mysql-connector-java"        % "5.1.21",
  "org.apache.commons"            %  "commons-email"               % "1.2",
  "org.apache.commons"            %  "commons-math"                % "2.2",
  "com.typesafe.play"             %% "play"                        % "2.3.7",
  "com.typesafe.play"             %% "play-jdbc"                   % "2.3.7",
  "com.typesafe.play"             %% "play-ws"                     % "2.3.7",
  "org.quartz-scheduler"          %  "quartz"                      % "2.2.1"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

linuxPackageMappings in Debian <+= (baseDirectory) map { bd =>
  (packageMapping(
    (bd / "scripts/start") -> "/opt/rearview/start")
    withUser "app" withGroup "devel" withPerms "0755")
}

linuxPackageMappings in Debian <+= (baseDirectory) map { bd =>
  (packageMapping(
    (bd / "scripts/rearview.conf") -> "/etc/init/rearview.conf")
    withUser "root" withGroup "root" withPerms "0644")
}

linuxPackageMappings <+= (baseDirectory) map { bd =>
  val src  = bd / "target/staged"
  val dest = "/opt/rearview/lib"
  LinuxPackageMapping(
    for {
      path <- (src ***).get
      if !path.isDirectory
    } yield path -> path.toString.replaceFirst(src.toString, dest)
  ) withUser "app" withGroup "devel" withPerms "0644"
}
