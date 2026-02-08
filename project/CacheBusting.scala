import sbt._
import sbt.Keys._
import java.security.MessageDigest

object CacheBusting {

  val generateAssetHashes = taskKey[Map[String, String]]("Generate hashes for static assets")
  val updateHtmlWithHashes = taskKey[Unit]("Update HTML files with hashed asset URLs")
  val cacheBust = taskKey[Unit]("Generate asset hashes and update HTML files")

  def settings: Seq[Def.Setting[_]] = Seq(
    generateAssetHashes := generateAssetHashesTask.value,
    updateHtmlWithHashes := updateHtmlWithHashesTask.value,
    cacheBust := cacheBustTask.value
  )

  private def generateAssetHashesTask: Def.Initialize[Task[Map[String, String]]] = Def.task {
    val log = streams.value.log
    val staticDir = baseDirectory.value / "jvm" / "src" / "main" / "resources" / "static"
    val jsDir = staticDir / "js"

    log.info("[CacheBusting] Generating asset hashes...")

    val jsFiles = (jsDir ** "*.js").filter(_.isFile).get()

    val hashes = jsFiles.map { file =>
      val hash = computeFileHash(file)
      val shortHash = hash.take(8)
      val fileName = file.getName
      val baseName = fileName.stripSuffix(".js")
      val hashedName = s"$baseName.$shortHash.js"

      log.info(s"[CacheBusting] $fileName -> $hashedName")

      (fileName, hashedName)
    }.toMap

    // Save hash mapping to a properties file for reference
    val hashMapFile = staticDir / "asset-hashes.properties"
    val content = hashes.map { case (original, hashed) => s"$original=$hashed" }.mkString("\n")
    IO.write(hashMapFile, content)

    log.info(s"[CacheBusting] Generated ${hashes.size} asset hash(es)")
    hashes
  }

  private def updateHtmlWithHashesTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = streams.value.log
    val hashes = generateAssetHashes.value
    val staticDir = baseDirectory.value / "jvm" / "src" / "main" / "resources" / "static"

    log.info("[CacheBusting] Updating HTML files with hashed asset URLs...")

    val htmlFiles = (staticDir ** "*.html").filter(_.isFile).get()

    htmlFiles.foreach { htmlFile =>
      val originalContent = IO.read(htmlFile)
      var updatedContent = originalContent

      hashes.foreach { case (originalName, hashedName) =>
        // Replace /static/js/main.js with /static/js/main.{hash}.js
        val pattern = s"/static/js/$originalName"
        val replacement = s"/static/js/$hashedName"
        updatedContent = updatedContent.replace(pattern, replacement)
      }

      if (updatedContent != originalContent) {
        IO.write(htmlFile, updatedContent)
        log.info(s"[CacheBusting] Updated ${htmlFile.getName}")
      } else {
        log.info(s"[CacheBusting] No changes needed for ${htmlFile.getName}")
      }
    }
  }

  private def cacheBustTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = streams.value.log
    val staticDir = baseDirectory.value / "jvm" / "src" / "main" / "resources" / "static"
    val jsDir = staticDir / "js"

    log.info("[CacheBusting] Starting cache busting process...")

    // Step 1: Generate hashes
    val hashes = generateAssetHashes.value

    // Step 2: Copy files with hashed names
    hashes.foreach { case (originalName, hashedName) =>
      val originalFile = jsDir / originalName
      val hashedFile = jsDir / hashedName

      if (originalFile.exists()) {
        IO.copyFile(originalFile, hashedFile)
        log.info(s"[CacheBusting] Copied $originalName to $hashedName")

        // Also copy source map if it exists
        val originalMap = jsDir / s"$originalName.map"
        if (originalMap.exists()) {
          val baseName = originalName.stripSuffix(".js")
          val hashedBaseName = hashedName.stripSuffix(".js")
          val hashedMap = jsDir / s"$hashedName.map"
          IO.copyFile(originalMap, hashedMap)

          // Update the sourceMapURL comment in the hashed JS file
          val jsContent = IO.read(hashedFile)
          val updatedJsContent = jsContent.replace(
            s"//# sourceMappingURL=$originalName.map",
            s"//# sourceMappingURL=$hashedName.map"
          )
          IO.write(hashedFile, updatedJsContent)
          log.info(s"[CacheBusting] Copied and updated source map for $originalName")
        }
      }
    }

    // Step 3: Update HTML files
    updateHtmlWithHashes.value

    log.info("[CacheBusting] Cache busting complete!")
  }

  private def computeFileHash(file: File): String = {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = IO.readBytes(file)
    val hashBytes = md.digest(bytes)
    hashBytes.map("%02x".format(_)).mkString
  }
}


