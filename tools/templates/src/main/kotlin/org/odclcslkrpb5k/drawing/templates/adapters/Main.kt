package org.odclcslkrpb5k.drawing.templates.adapters

import java.io.File
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.Regions
import org.odclcslkrpb5k.drawing.core.RegionsCodec
import org.odclcslkrpb5k.drawing.core.Templates
import org.odclcslkrpb5k.drawing.templates.Manifest
import org.odclcslkrpb5k.drawing.templates.PageRules
import org.odclcslkrpb5k.drawing.templates.lineArt
import org.odclcslkrpb5k.drawing.templates.lines
import org.odclcslkrpb5k.drawing.templates.problems

/**
 * Builds the app's coloring pages: each `<name>.svg` in the pages directory is rendered by resvg,
 * labelled into regions and checked; a page that passes becomes `<name>.rgn` (its regions) and
 * `<name>.png` (its line art) in the output directory, and `pages.txt` lists them in the manifest's
 * order. Any page that breaks a rule fails the build, naming the page and the rule.
 *
 * Usage: `templates <pages dir> <out dir> <work dir> <resvg>`
 */
fun main(args: Array<String>) {
    if (args.size != ARGS) {
        Files.say("usage: templates <pages dir> <out dir> <work dir> <resvg>")
        Files.exit(USAGE)
    }
    val dirs = Dirs(File(args[0]), File(args[1]), File(args[2]), File(args[RESVG_ARG]))
    val manifest = Manifest.parse(Files.readText(File(dirs.pages, MANIFEST)).orFail()).orFail()
    val svgs = Files.svgNames(dirs.pages).orFail()
    Files.makeDirs(dirs.out).orFail()
    Files.makeDirs(dirs.work).orFail()
    val found = mutableListOf<String>()
    for (name in manifest.keys - svgs.toSet()) found +=
        "$name: in $MANIFEST but $name.svg is missing"
    for (name in svgs) found += build(name, manifest[name], dirs)
    if (found.isNotEmpty()) {
        found.forEach(Files::say)
        Files.exit(FAILED)
    }
    val list = manifest.keys.joinToString("") { "$it\n" }
    Files.write(File(dirs.out, LIST), list.toByteArray(Charsets.UTF_8)).orFail()
}

/** Where the pages come from and go, and the resvg that renders them. */
private class Dirs(val pages: File, val out: File, val work: File, val resvg: File)

/** One page: rendered, labelled, checked, and written only when it breaks no rule. */
private fun build(name: String, expected: Int?, dirs: Dirs): List<String> {
    if (!Templates.isName(name)) return listOf("$name.svg: names are a-z, 0-9 and '-'")
    val png = File(dirs.work, "$name.png")
    Files.render(dirs.resvg, File(dirs.pages, "$name.svg"), png, PageRules.WIDTH, PageRules.HEIGHT)
        .orFail()
    val raster = Files.readPng(png).orFail()
    val regions = Regions.label(raster.width, raster.height, lines(raster), PageRules.MIN_PIXELS)
    val found = problems(name, raster, regions, expected)
    if (found.isEmpty()) {
        Files.write(File(dirs.out, "$name.rgn"), RegionsCodec.encode(regions)).orFail()
        Files.writePng(File(dirs.out, "$name.png"), raster.width, raster.height, lineArt(raster))
            .orFail()
        Files.say("$name: ${regions.count} regions")
    }
    return found
}

/** The value, or the build stops here with the failure's sentence. */
private fun <T> Outcome<T, String>.orFail(): T =
    when (this) {
        is Outcome.Ok -> value
        is Outcome.Err -> {
            Files.say(error)
            Files.exit(FAILED)
        }
    }

private const val ARGS = 4
private const val RESVG_ARG = 3
private const val USAGE = 2
private const val FAILED = 1
private const val MANIFEST = "pages.tsv"
private const val LIST = "pages.txt"
