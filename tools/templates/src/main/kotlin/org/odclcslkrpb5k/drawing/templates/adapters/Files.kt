package org.odclcslkrpb5k.drawing.templates.adapters

import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.system.exitProcess
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.templates.Raster

/** The builder's contact with files, resvg and the console; every failure is a sentence. */
object Files {
    fun readText(file: File): Outcome<String, String> = io("read $file") { file.readText() }

    /** The names of the `.svg` files in [dir], without the extension, sorted. */
    fun svgNames(dir: File): Outcome<List<String>, String> =
        io("list $dir") {
            val names = dir.list() ?: throw IOException("not a directory")
            names.filter { it.endsWith(SVG) }.map { it.removeSuffix(SVG) }.sorted()
        }

    /** Renders [svg] to [png] at exactly [width] by [height] with resvg at [resvg]. */
    fun render(resvg: File, svg: File, png: File, width: Int, height: Int): Outcome<Unit, String> =
        io("render $svg") {
            val process =
                ProcessBuilder(
                        resvg.path,
                        "--width",
                        width.toString(),
                        "--height",
                        height.toString(),
                        svg.path,
                        png.path,
                    )
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(RENDER_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IOException("resvg took longer than $RENDER_SECONDS s")
            }
            if (process.exitValue() != 0) throw IOException("resvg failed: $output")
        }

    fun readPng(png: File): Outcome<Raster, String> =
        io("read $png") {
            val image = ImageIO.read(png) ?: throw IOException("not an image")
            Raster(
                image.width,
                image.height,
                image.getRGB(0, 0, image.width, image.height, null, 0, image.width),
            )
        }

    fun writePng(png: File, width: Int, height: Int, argb: IntArray): Outcome<Unit, String> =
        io("write $png") {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, width, height, argb, 0, width)
            if (!ImageIO.write(image, "png", png)) throw IOException("no PNG writer")
        }

    fun write(file: File, bytes: ByteArray): Outcome<Unit, String> =
        io("write $file") { file.writeBytes(bytes) }

    fun makeDirs(dir: File): Outcome<Unit, String> =
        io("make $dir") { if (!dir.isDirectory && !dir.mkdirs()) throw IOException("mkdirs") }

    /** A line for the person running the build. */
    fun say(line: String) = System.err.println(line)

    fun exit(code: Int): Nothing = exitProcess(code)

    private inline fun <T> io(what: String, block: () -> T): Outcome<T, String> =
        try {
            Outcome.Ok(block())
        } catch (e: IOException) {
            Outcome.Err("could not $what: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: SecurityException) {
            Outcome.Err("could not $what: ${e.message ?: e.javaClass.simpleName}")
        }

    private const val SVG = ".svg"
    private const val RENDER_SECONDS = 60L
}
