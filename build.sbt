import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.debian.Keys._
import com.typesafe.sbt.packager.linux.LinuxPackageMapping

packagerSettings

maintainer in Debian:= "Jeff Simpson <jeff@victorops.com>"

packageSummary := "Monitoring framework for Graphite metrics"

packageDescription in Debian:= "Monitoring framework for Graphite metrics"

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
