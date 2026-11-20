package com.example.typography

import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Parses real SFNT/OpenType binary tables (OTTO/CFF and TTF/glyf):
 * - head, maxp, OS/2, name, cmap (format 4 & 12)
 * - GSUB (ScriptList, FeatureList, LookupList Types 1..7 including Extension & AlternateSubst)
 * - GPOS (ScriptList, FeatureList, LookupList)
 *
 * Zero faking: only features and alternate glyph counts physically compiled into the font
 * tables are reported.
 */
object OpenTypeFontInspector {

    private data class TableRecord(val offset: Int, val length: Int)

    private data class FeatureAccumulator(
        val tag: String,
        var source: OpenTypeTableSource,
        val scripts: MutableSet<String> = linkedSetOf(),
        val gsubLookupIndices: MutableSet<Int> = linkedSetOf(),
        val gposLookupIndices: MutableSet<Int> = linkedSetOf(),
        var uiNameId: Int? = null
    )

    private data class LookupInspection(
        val coveredGlyphs: Set<Int> = emptySet(),
        val singleSubstitutions: Map<Int, Int> = emptyMap(),
        val alternates: Map<Int, List<Int>> = emptyMap(),
        val maxAlternateCount: Int = 1
    )

    private val mandatoryArabicOrEngineTags = setOf(
        "ccmp", "locl", "init", "medi", "fina", "isol", "rlig", "mark", "mkmk", "curs",
        "rtlm", "rtla", "rvrn", "abvm", "blwm", "pref", "blwf", "abvf", "pstf", "cjct",
        "vatu", "nukt", "akhn", "rphf", "rkrf", "half", "pres", "abvs", "blws", "psts",
        "haln", "dist"
    )

    private val defaultOnTags = setOf(
        "liga", "clig", "calt", "kern", "rlig", "ccmp", "locl",
        "init", "medi", "fina", "isol", "mark", "mkmk", "curs", "rtlm"
    )

    fun inspect(file: File): FontMetadata {
        return inspect(file.readBytes(), fallbackName = file.nameWithoutExtension)
    }

    fun inspect(inputStream: InputStream, fallbackName: String = "Custom Font"): FontMetadata {
        return inspect(inputStream.readBytes(), fallbackName = fallbackName)
    }

    fun inspect(bytes: ByteArray, fallbackName: String = "Custom Font"): FontMetadata {
        if (bytes.size < 12) {
            return emptyMetadata(fallbackName)
        }
        val reader = SfntReader(bytes)
        val sfntOffset = resolveSfntOffset(reader)
        if (sfntOffset < 0 || sfntOffset + 12 > bytes.size) {
            return emptyMetadata(fallbackName)
        }

        val sfntVersion = reader.u32(sfntOffset)
        val format = when (sfntVersion) {
            0x4F54544FL -> FontFormat.OTF_CFF // 'OTTO'
            0x00010000L, 0x74727565L -> FontFormat.TTF_GLYF // 1.0 or 'true'
            else -> FontFormat.UNKNOWN
        }
        val numTables = reader.u16(sfntOffset + 4)
        val tables = linkedMapOf<String, TableRecord>()
        var dirPos = sfntOffset + 12
        for (i in 0 until numTables) {
            if (dirPos + 16 > bytes.size) break
            val tag = reader.tag(dirPos)
            val offset = reader.u32(dirPos + 8).toInt()
            val length = reader.u32(dirPos + 12).toInt()
            if (offset >= 0 && length > 0 && offset + length <= bytes.size) {
                tables[tag] = TableRecord(offset, length)
            }
            dirPos += 16
        }

        val unitsPerEm = tables["head"]?.let { head ->
            if (head.length >= 20) reader.u16(head.offset + 18) else 1000
        } ?: 1000

        val glyphCount = tables["maxp"]?.let { maxp ->
            if (maxp.length >= 6) reader.u16(maxp.offset + 4) else 0
        } ?: 0

        val nameMap = tables["name"]?.let { parseNameTable(reader, it) } ?: emptyMap()
        val familyName = nameMap[16] ?: nameMap[1] ?: fallbackName
        val subfamilyName = nameMap[17] ?: nameMap[2] ?: "Regular"
        val fullName = nameMap[4] ?: "$familyName $subfamilyName".trim()

        val cmap = tables["cmap"]?.let { parseCmap(reader, it) } ?: emptyMap()
        val os2Arabic = tables["OS/2"]?.let { os2 ->
            if (os2.length >= 46) (reader.u32(os2.offset + 42) and (1L shl 13)) != 0L else false
        } ?: false
        val os2Latin = tables["OS/2"]?.let { os2 ->
            if (os2.length >= 46) (reader.u32(os2.offset + 42) and 1L) != 0L else false
        } ?: false

        val hasArabicCodepoints = (0x0621..0x064A).any { (cmap[it] ?: 0) > 0 }
        val hasLatinCodepoints = ('A'.code..'Z'.code).any { (cmap[it] ?: 0) > 0 }

        val accumulators = linkedMapOf<String, FeatureAccumulator>()
        val scripts = linkedSetOf<String>()
        val gsubLookups = mutableListOf<LookupInspection>()
        val gposLookups = mutableListOf<LookupInspection>()

        tables["GSUB"]?.let { gsub ->
            parseGsubOrGpos(
                reader = reader,
                table = gsub,
                isGsub = true,
                accumulators = accumulators,
                scriptsOut = scripts,
                lookupsOut = gsubLookups
            )
        }

        tables["GPOS"]?.let { gpos ->
            parseGsubOrGpos(
                reader = reader,
                table = gpos,
                isGsub = false,
                accumulators = accumulators,
                scriptsOut = scripts,
                lookupsOut = gposLookups
            )
        }

        val features = accumulators.values.map { acc ->
            val covered = linkedSetOf<Int>()
            var maxAlts = 1
            for (idx in acc.gsubLookupIndices) {
                val info = gsubLookups.getOrNull(idx) ?: continue
                covered.addAll(info.coveredGlyphs)
                if (info.maxAlternateCount > maxAlts) {
                    maxAlts = info.maxAlternateCount
                }
            }
            for (idx in acc.gposLookupIndices) {
                val info = gposLookups.getOrNull(idx) ?: continue
                covered.addAll(info.coveredGlyphs)
            }
            val customUiName = acc.uiNameId?.let { nameMap[it] }?.takeIf { it.isNotBlank() }
            val (defaultTitle, desc) = describeFeatureTag(acc.tag)
            val displayName = customUiName ?: defaultTitle
            val isToggleable = acc.tag !in mandatoryArabicOrEngineTags
            OpenTypeFeatureInfo(
                tag = acc.tag,
                displayName = displayName,
                description = desc,
                source = acc.source,
                scripts = acc.scripts.toSet(),
                maxAlternateCount = maxAlts,
                coveredGlyphCount = covered.size,
                isUserToggleable = isToggleable,
                defaultEnabled = acc.tag in defaultOnTags
            )
        }.sortedWith(compareBy<OpenTypeFeatureInfo> { featureSortPriority(it.tag) }.thenBy { it.tag })

        val supportsArabic = hasArabicCodepoints || os2Arabic || "arab" in scripts
        val supportsLatin = hasLatinCodepoints || os2Latin || "latn" in scripts
        val allTags = features.map { it.tag }.toSet()

        return FontMetadata(
            familyName = familyName,
            subfamilyName = subfamilyName,
            fullName = fullName,
            format = format,
            unitsPerEm = unitsPerEm,
            glyphCount = glyphCount,
            supportsArabic = supportsArabic,
            supportsLatin = supportsLatin,
            availableScripts = scripts.toSet(),
            features = features,
            hasMarkPositioning = "mark" in allTags || "mkmk" in allTags,
            hasCursiveAttachment = "curs" in allTags,
            hasArabicShaping = "init" in allTags && "medi" in allTags && "fina" in allTags
        )
    }

    /**
     * Inspects which characters in [text] have genuine OpenType substitutions/alternates in the font
     * (checking both nominal cmap glyphs and Arabic positional init/medi/fina/isol glyphs).
     */
    fun findAlternatesForText(
        bytes: ByteArray,
        text: String,
        targetFeatures: Set<String> = setOf("salt", "swsh", "aalt")
    ): List<GlyphAlternateMatch> {
        if (bytes.size < 12 || text.isEmpty()) return emptyList()
        val reader = SfntReader(bytes)
        val sfntOffset = resolveSfntOffset(reader)
        if (sfntOffset < 0) return emptyList()

        val numTables = reader.u16(sfntOffset + 4)
        var cmapTable: TableRecord? = null
        var gsubTable: TableRecord? = null
        var dirPos = sfntOffset + 12
        for (i in 0 until numTables) {
            if (dirPos + 16 > bytes.size) break
            val tag = reader.tag(dirPos)
            val offset = reader.u32(dirPos + 8).toInt()
            val length = reader.u32(dirPos + 12).toInt()
            if (offset >= 0 && length > 0 && offset + length <= bytes.size) {
                if (tag == "cmap") cmapTable = TableRecord(offset, length)
                if (tag == "GSUB") gsubTable = TableRecord(offset, length)
            }
            dirPos += 16
        }
        val cmap = cmapTable?.let { parseCmap(reader, it) } ?: return emptyList()
        val gsub = gsubTable ?: return emptyList()

        val accumulators = linkedMapOf<String, FeatureAccumulator>()
        val scripts = linkedSetOf<String>()
        val lookups = mutableListOf<LookupInspection>()
        parseGsubOrGpos(reader, gsub, true, accumulators, scripts, lookups)

        // Collect nominal -> Arabic positional forms so Arabic letters match salt/ssXX on their joined forms too
        val positionalExpansion = linkedMapOf<Int, MutableSet<Int>>()
        for (posTag in listOf("init", "medi", "fina", "isol")) {
            val acc = accumulators[posTag] ?: continue
            for (lIdx in acc.gsubLookupIndices) {
                val lookup = lookups.getOrNull(lIdx) ?: continue
                for ((src, dst) in lookup.singleSubstitutions) {
                    positionalExpansion.getOrPut(src) { linkedSetOf(src) }.add(dst)
                }
            }
        }

        val matches = mutableListOf<GlyphAlternateMatch>()
        var charIndex = 0
        while (charIndex < text.length) {
            val cp = text.codePointAt(charIndex)
            val charLen = Character.charCount(cp)
            val baseGlyph = cmap[cp]
            if (baseGlyph != null && baseGlyph > 0) {
                val candidateGlyphs = positionalExpansion[baseGlyph] ?: setOf(baseGlyph)
                for (tag in targetFeatures) {
                    val acc = accumulators[tag] ?: continue
                    val foundAlts = linkedSetOf<Int>()
                    var coveredByContextualOrLigature = false
                    for (lIdx in acc.gsubLookupIndices) {
                        val lookup = lookups.getOrNull(lIdx) ?: continue
                        for (g in candidateGlyphs) {
                            lookup.alternates[g]?.let { foundAlts.addAll(it) }
                            lookup.singleSubstitutions[g]?.let { foundAlts.add(it) }
                            if (g in lookup.coveredGlyphs) {
                                coveredByContextualOrLigature = true
                            }
                        }
                    }
                    if (foundAlts.isNotEmpty() || coveredByContextualOrLigature) {
                        matches.add(
                            GlyphAlternateMatch(
                                charIndex = charIndex,
                                codePoint = cp,
                                character = String(Character.toChars(cp)),
                                baseGlyphId = baseGlyph,
                                featureTag = tag,
                                alternateGlyphIds = if (foundAlts.isNotEmpty()) foundAlts.toList() else listOf(baseGlyph)
                            )
                        )
                    }
                }
            }
            charIndex += charLen
        }
        return matches
    }

    private fun resolveSfntOffset(reader: SfntReader): Int {
        val magic = reader.tag(0)
        return if (magic == "ttcf") {
            if (reader.size < 16) -1 else reader.u32(12).toInt()
        } else {
            0
        }
    }

    private fun parseNameTable(reader: SfntReader, table: TableRecord): Map<Int, String> {
        if (table.length < 6) return emptyMap()
        val count = reader.u16(table.offset + 2)
        val stringOffset = table.offset + reader.u16(table.offset + 4)
        val bestByNameId = linkedMapOf<Int, Pair<Int, String>>() // nameId -> (priority, value)

        for (i in 0 until count) {
            val recPos = table.offset + 6 + i * 12
            if (recPos + 12 > table.offset + table.length) break
            val platformId = reader.u16(recPos)
            val encodingId = reader.u16(recPos + 2)
            val languageId = reader.u16(recPos + 4)
            val nameId = reader.u16(recPos + 6)
            val length = reader.u16(recPos + 8)
            val strOff = stringOffset + reader.u16(recPos + 10)
            if (length <= 0 || strOff < table.offset || strOff + length > table.offset + table.length) {
                continue
            }

            val priority = when {
                platformId == 3 && languageId == 0x0409 -> 100
                platformId == 3 -> 80
                platformId == 0 -> 70
                platformId == 1 && languageId == 0 -> 50
                else -> 10
            }

            val text = when (platformId) {
                0, 3 -> String(reader.slice(strOff, length), StandardCharsets.UTF_16BE)
                1 -> {
                    if (encodingId == 0) {
                        String(reader.slice(strOff, length), StandardCharsets.ISO_8859_1)
                    } else {
                        String(reader.slice(strOff, length), StandardCharsets.UTF_8)
                    }
                }
                else -> String(reader.slice(strOff, length), StandardCharsets.ISO_8859_1)
            }.trim('\u0000', ' ')

            if (text.isNotEmpty()) {
                val existing = bestByNameId[nameId]
                if (existing == null || priority > existing.first) {
                    bestByNameId[nameId] = priority to text
                }
            }
        }
        return bestByNameId.mapValues { it.value.second }
    }

    private fun parseCmap(reader: SfntReader, table: TableRecord): Map<Int, Int> {
        if (table.length < 4) return emptyMap()
        val numTables = reader.u16(table.offset + 2)
        var bestFormat12Offset = -1
        var bestFormat4Offset = -1
        var bestFormat4Score = -1

        for (i in 0 until numTables) {
            val recPos = table.offset + 4 + i * 8
            if (recPos + 8 > table.offset + table.length) break
            val platformId = reader.u16(recPos)
            val encodingId = reader.u16(recPos + 2)
            val subOffset = table.offset + reader.u32(recPos + 4).toInt()
            if (subOffset + 4 > table.offset + table.length) continue
            val format = reader.u16(subOffset)
            if (format == 12 && (platformId == 3 || platformId == 0)) {
                if (bestFormat12Offset < 0 || (platformId == 3 && encodingId == 10)) {
                    bestFormat12Offset = subOffset
                }
            } else if (format == 4) {
                val score = when {
                    platformId == 3 && encodingId == 1 -> 100
                    platformId == 0 -> 90
                    platformId == 3 -> 70
                    else -> 20
                }
                if (score > bestFormat4Score) {
                    bestFormat4Score = score
                    bestFormat4Offset = subOffset
                }
            }
        }

        val map = HashMap<Int, Int>(1024)
        if (bestFormat12Offset >= 0) {
            parseCmapFormat12(reader, bestFormat12Offset, map)
        }
        if (map.isEmpty() && bestFormat4Offset >= 0) {
            parseCmapFormat4(reader, bestFormat4Offset, map)
        }
        return map
    }

    private fun parseCmapFormat4(reader: SfntReader, subOffset: Int, out: MutableMap<Int, Int>) {
        if (subOffset + 14 > reader.size) return
        val segCountX2 = reader.u16(subOffset + 6)
        val segCount = segCountX2 / 2
        val endCodeBase = subOffset + 14
        val startCodeBase = endCodeBase + segCountX2 + 2
        val idDeltaBase = startCodeBase + segCountX2
        val idRangeOffsetBase = idDeltaBase + segCountX2
        if (idRangeOffsetBase + segCountX2 > reader.size) return

        for (i in 0 until segCount) {
            val endCode = reader.u16(endCodeBase + i * 2)
            val startCode = reader.u16(startCodeBase + i * 2)
            if (startCode == 0xFFFF && endCode == 0xFFFF) break
            val idDelta = reader.s16(idDeltaBase + i * 2)
            val idRangeOffPos = idRangeOffsetBase + i * 2
            val idRangeOffset = reader.u16(idRangeOffPos)

            for (c in startCode..endCode) {
                val gid = if (idRangeOffset == 0) {
                    (c + idDelta) and 0xFFFF
                } else {
                    val glyphAddr = idRangeOffPos + idRangeOffset + 2 * (c - startCode)
                    if (glyphAddr + 2 > reader.size) 0 else {
                        val raw = reader.u16(glyphAddr)
                        if (raw == 0) 0 else (raw + idDelta) and 0xFFFF
                    }
                }
                if (gid > 0) out[c] = gid
            }
        }
    }

    private fun parseCmapFormat12(reader: SfntReader, subOffset: Int, out: MutableMap<Int, Int>) {
        if (subOffset + 16 > reader.size) return
        val numGroups = reader.u32(subOffset + 12).toInt().coerceAtMost(100_000)
        var pos = subOffset + 16
        for (i in 0 until numGroups) {
            if (pos + 12 > reader.size) break
            val startChar = reader.u32(pos).toInt()
            val endChar = reader.u32(pos + 4).toInt()
            val startGid = reader.u32(pos + 8).toInt()
            val span = (endChar - startChar).coerceAtMost(65536)
            for (k in 0..span) {
                val gid = startGid + k
                if (gid > 0) out[startChar + k] = gid
            }
            pos += 12
        }
    }

    private fun parseGsubOrGpos(
        reader: SfntReader,
        table: TableRecord,
        isGsub: Boolean,
        accumulators: MutableMap<String, FeatureAccumulator>,
        scriptsOut: MutableSet<String>,
        lookupsOut: MutableList<LookupInspection>
    ) {
        if (table.length < 10) return
        val base = table.offset
        val limit = table.offset + table.length

        val scriptListOff = base + reader.u16(base + 4)
        val featureListOff = base + reader.u16(base + 6)
        val lookupListOff = base + reader.u16(base + 8)

        // 1. Parse LookupList
        if (lookupListOff + 2 <= limit) {
            val lookupCount = reader.u16(lookupListOff)
            for (i in 0 until lookupCount) {
                val ptrPos = lookupListOff + 2 + i * 2
                if (ptrPos + 2 > limit) break
                val lookupOff = lookupListOff + reader.u16(ptrPos)
                val inspection = if (lookupOff + 6 <= limit) {
                    if (isGsub) parseGsubLookup(reader, lookupOff, limit)
                    else parseGposLookup(reader, lookupOff, limit)
                } else {
                    LookupInspection()
                }
                lookupsOut.add(inspection)
            }
        }

        // 2. Map FeatureIndex -> Script tags via ScriptList
        val featureToScripts = HashMap<Int, MutableSet<String>>()
        if (scriptListOff + 2 <= limit) {
            val scriptCount = reader.u16(scriptListOff)
            for (i in 0 until scriptCount) {
                val recPos = scriptListOff + 2 + i * 6
                if (recPos + 6 > limit) break
                val scriptTag = reader.tag(recPos)
                scriptsOut.add(scriptTag)
                val scriptOff = scriptListOff + reader.u16(recPos + 4)
                if (scriptOff + 4 > limit) continue

                val langSysOffsets = mutableListOf<Int>()
                val defaultLangOff = reader.u16(scriptOff)
                if (defaultLangOff != 0) langSysOffsets.add(scriptOff + defaultLangOff)
                val langSysCount = reader.u16(scriptOff + 2)
                for (j in 0 until langSysCount) {
                    val lPos = scriptOff + 4 + j * 6
                    if (lPos + 6 > limit) break
                    langSysOffsets.add(scriptOff + reader.u16(lPos + 4))
                }

                for (langOff in langSysOffsets) {
                    if (langOff + 6 > limit) continue
                    val reqFeature = reader.u16(langOff + 2)
                    if (reqFeature != 0xFFFF) {
                        featureToScripts.getOrPut(reqFeature) { linkedSetOf() }.add(scriptTag)
                    }
                    val fCount = reader.u16(langOff + 4)
                    for (k in 0 until fCount) {
                        val fPos = langOff + 6 + k * 2
                        if (fPos + 2 > limit) break
                        val fIdx = reader.u16(fPos)
                        featureToScripts.getOrPut(fIdx) { linkedSetOf() }.add(scriptTag)
                    }
                }
            }
        }

        // 3. Parse FeatureList
        if (featureListOff + 2 <= limit) {
            val featureCount = reader.u16(featureListOff)
            for (i in 0 until featureCount) {
                val recPos = featureListOff + 2 + i * 6
                if (recPos + 6 > limit) break
                val tag = reader.tag(recPos)
                val featOff = featureListOff + reader.u16(recPos + 4)
                if (featOff + 4 > limit) continue

                val featParamsRel = reader.u16(featOff)
                val lookupCount = reader.u16(featOff + 2)

                val acc = accumulators.getOrPut(tag) {
                    FeatureAccumulator(
                        tag = tag,
                        source = if (isGsub) OpenTypeTableSource.GSUB else OpenTypeTableSource.GPOS
                    )
                }
                if ((isGsub && acc.source == OpenTypeTableSource.GPOS) ||
                    (!isGsub && acc.source == OpenTypeTableSource.GSUB)
                ) {
                    acc.source = OpenTypeTableSource.BOTH
                }
                featureToScripts[i]?.let { acc.scripts.addAll(it) }

                if (featParamsRel != 0 && acc.uiNameId == null) {
                    val paramsOff = featOff + featParamsRel
                    if (paramsOff + 4 <= limit) {
                        if (tag.startsWith("ss") || tag.startsWith("cv")) {
                            val nameId = reader.u16(paramsOff + 2)
                            if (nameId >= 256) acc.uiNameId = nameId
                        }
                    }
                }

                for (j in 0 until lookupCount) {
                    val lPos = featOff + 4 + j * 2
                    if (lPos + 2 > limit) break
                    val lIdx = reader.u16(lPos)
                    if (isGsub) acc.gsubLookupIndices.add(lIdx)
                    else acc.gposLookupIndices.add(lIdx)
                }
            }
        }
    }

    private fun parseGsubLookup(
        reader: SfntReader,
        lookupOff: Int,
        limit: Int
    ): LookupInspection {
        val lookupType = reader.u16(lookupOff)
        val subTableCount = reader.u16(lookupOff + 4)
        val covered = linkedSetOf<Int>()
        val singleMap = linkedMapOf<Int, Int>()
        val altMap = linkedMapOf<Int, List<Int>>()
        var maxAlts = 1

        for (i in 0 until subTableCount) {
            val ptrPos = lookupOff + 6 + i * 2
            if (ptrPos + 2 > limit) break
            val subOff = lookupOff + reader.u16(ptrPos)
            if (subOff + 4 > limit) continue

            val actualType: Int
            val actualOff: Int
            if (lookupType == 7) { // ExtensionSubst
                if (subOff + 8 > limit) continue
                actualType = reader.u16(subOff + 2)
                actualOff = subOff + reader.u32(subOff + 4).toInt()
            } else {
                actualType = lookupType
                actualOff = subOff
            }
            if (actualOff < 0 || actualOff + 4 > limit) continue

            val format = reader.u16(actualOff)
            val covRel = reader.u16(actualOff + 2)
            val covGlyphs = if (covRel > 0 && actualOff + covRel + 4 <= limit) {
                parseCoverage(reader, actualOff + covRel, limit)
            } else emptyList()
            covered.addAll(covGlyphs)

            when (actualType) {
                1 -> { // SingleSubst
                    if (format == 1 && actualOff + 6 <= limit) {
                        val delta = reader.s16(actualOff + 4)
                        for (g in covGlyphs) {
                            singleMap[g] = (g + delta) and 0xFFFF
                        }
                    } else if (format == 2 && actualOff + 6 <= limit) {
                        val glyphCount = reader.u16(actualOff + 4)
                        for (gIdx in 0 until minOf(glyphCount, covGlyphs.size)) {
                            val pos = actualOff + 6 + gIdx * 2
                            if (pos + 2 > limit) break
                            singleMap[covGlyphs[gIdx]] = reader.u16(pos)
                        }
                    }
                }
                3 -> { // AlternateSubst
                    if (format == 1 && actualOff + 6 <= limit) {
                        val altSetCount = reader.u16(actualOff + 4)
                        for (gIdx in 0 until minOf(altSetCount, covGlyphs.size)) {
                            val ptr = actualOff + 6 + gIdx * 2
                            if (ptr + 2 > limit) break
                            val altSetOff = actualOff + reader.u16(ptr)
                            if (altSetOff + 2 > limit) continue
                            val altCount = reader.u16(altSetOff)
                            if (altCount > maxAlts) maxAlts = altCount
                            val alts = ArrayList<Int>(altCount)
                            for (a in 0 until altCount) {
                                val aPos = altSetOff + 2 + a * 2
                                if (aPos + 2 > limit) break
                                alts.add(reader.u16(aPos))
                            }
                            if (alts.isNotEmpty()) {
                                altMap[covGlyphs[gIdx]] = alts
                            }
                        }
                    }
                }
            }
        }
        return LookupInspection(
            coveredGlyphs = covered,
            singleSubstitutions = singleMap,
            alternates = altMap,
            maxAlternateCount = maxAlts
        )
    }

    private fun parseGposLookup(
        reader: SfntReader,
        lookupOff: Int,
        limit: Int
    ): LookupInspection {
        val lookupType = reader.u16(lookupOff)
        val subTableCount = reader.u16(lookupOff + 4)
        val covered = linkedSetOf<Int>()

        for (i in 0 until subTableCount) {
            val ptrPos = lookupOff + 6 + i * 2
            if (ptrPos + 2 > limit) break
            val subOff = lookupOff + reader.u16(ptrPos)
            if (subOff + 4 > limit) continue

            val actualOff = if (lookupType == 9) { // ExtensionPos
                if (subOff + 8 > limit) continue
                subOff + reader.u32(subOff + 4).toInt()
            } else {
                subOff
            }
            if (actualOff < 0 || actualOff + 4 > limit) continue
            val covRel = reader.u16(actualOff + 2)
            if (covRel > 0 && actualOff + covRel + 4 <= limit) {
                covered.addAll(parseCoverage(reader, actualOff + covRel, limit))
            }
        }
        return LookupInspection(coveredGlyphs = covered)
    }

    private fun parseCoverage(reader: SfntReader, covOff: Int, limit: Int): List<Int> {
        if (covOff + 4 > limit) return emptyList()
        val format = reader.u16(covOff)
        val count = reader.u16(covOff + 2)
        val out = ArrayList<Int>(count.coerceAtMost(4096))
        if (format == 1) {
            for (i in 0 until count) {
                val pos = covOff + 4 + i * 2
                if (pos + 2 > limit) break
                out.add(reader.u16(pos))
            }
        } else if (format == 2) {
            for (i in 0 until count) {
                val pos = covOff + 4 + i * 6
                if (pos + 6 > limit) break
                val startGlyph = reader.u16(pos)
                val endGlyph = reader.u16(pos + 2)
                for (g in startGlyph..endGlyph) {
                    out.add(g)
                    if (out.size >= 16384) return out
                }
            }
        }
        return out
    }

    private fun featureSortPriority(tag: String): Int = when {
        tag == "salt" -> 0
        tag == "swsh" || tag == "cswh" -> 1
        tag == "calt" -> 2
        tag == "liga" -> 3
        tag == "dlig" -> 4
        tag == "kern" -> 5
        tag.startsWith("ss") -> 10
        tag == "aalt" -> 15
        tag.startsWith("cv") -> 20
        tag in setOf("smcp", "c2sc", "titl", "onum", "tnum", "pnum", "frac", "ordn", "zero") -> 30
        tag !in mandatoryArabicOrEngineTags -> 40
        else -> 90
    }

    private fun describeFeatureTag(tag: String): Pair<String, String> = when (tag) {
        "salt" -> "Stylistic Alternates" to "Alternate letterforms and calligraphic variants"
        "calt" -> "Contextual Alternates" to "Context-aware Arabic & Latin letterforms"
        "liga" -> "Standard Ligatures" to "Common ligatures (e.g., Lam-Alef, fi, fl)"
        "dlig" -> "Discretionary Ligatures" to "Decorative & optional artistic ligatures"
        "clig" -> "Contextual Ligatures" to "Context-sensitive ligature substitutions"
        "swsh" -> "Swashes" to "Flourished entry/exit strokes and swash forms"
        "cswh" -> "Contextual Swashes" to "Position-aware swash flourishes"
        "kern" -> "Kerning" to "Optical spacing adjustments between glyph pairs"
        "aalt" -> "Access All Alternates" to "All alternate glyphs defined in the font"
        "smcp" -> "Small Capitals" to "Lowercase letters styled as small capitals"
        "c2sc" -> "Caps to Small Caps" to "Uppercase letters converted to small capitals"
        "onum" -> "Oldstyle Figures" to "Varying-height numerals for editorial rhythm"
        "tnum" -> "Tabular Figures" to "Monospaced numerals for clean alignment"
        "pnum" -> "Proportional Figures" to "Naturally spaced numerals"
        "lnum" -> "Lining Figures" to "Uniform cap-height numerals"
        "frac" -> "Fractions" to "Diagonal typographic fractions"
        "ordn" -> "Ordinals" to "Superscript ordinal indicators"
        "zero" -> "Slashed Zero" to "Distinguished zero glyph with slash"
        "titl" -> "Titling Alternates" to "Refined forms tuned for large headlines"
        "init" -> "Initial Forms" to "Arabic word-initial letter shaping"
        "medi" -> "Medial Forms" to "Arabic word-medial letter shaping"
        "fina" -> "Final Forms" to "Arabic word-final letter shaping"
        "isol" -> "Isolated Forms" to "Arabic isolated letterforms"
        "rlig" -> "Required Ligatures" to "Mandatory Arabic Lam-Alef & script ligatures"
        "mark" -> "Mark Positioning" to "Arabic diacritic (harakat) anchor placement"
        "mkmk" -> "Mark-to-Mark" to "Stacked diacritics (shadda + fatha/kasra)"
        "curs" -> "Cursive Positioning" to "Baseline-aligned Arabic cursive connections"
        "ccmp" -> "Glyph Composition" to "Character & diacritic decomposition/composition"
        "locl" -> "Localized Forms" to "Language-specific glyph variants"
        "rtlm" -> "RTL Mirrored Forms" to "Right-to-left mirrored glyphs"
        else -> when {
            tag.startsWith("ss") -> "Stylistic Set ${tag.removePrefix("ss")}" to "Curated glyph set variant ($tag)"
            tag.startsWith("cv") -> "Character Variant ${tag.removePrefix("cv")}" to "Per-character stylistic variant ($tag)"
            else -> tag.uppercase() to "OpenType feature ($tag)"
        }
    }

    private fun emptyMetadata(name: String) = FontMetadata(
        familyName = name,
        subfamilyName = "Regular",
        fullName = name,
        format = FontFormat.UNKNOWN,
        unitsPerEm = 1000,
        glyphCount = 0,
        supportsArabic = false,
        supportsLatin = false,
        availableScripts = emptySet(),
        features = emptyList(),
        hasMarkPositioning = false,
        hasCursiveAttachment = false,
        hasArabicShaping = false
    )

    private class SfntReader(private val data: ByteArray) {
        val size: Int get() = data.size

        fun u16(offset: Int): Int {
            if (offset < 0 || offset + 2 > data.size) return 0
            return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        }

        fun s16(offset: Int): Int {
            val v = u16(offset)
            return if (v >= 0x8000) v - 0x10000 else v
        }

        fun u32(offset: Int): Long {
            if (offset < 0 || offset + 4 > data.size) return 0L
            return ((data[offset].toLong() and 0xFF) shl 24) or
                ((data[offset + 1].toLong() and 0xFF) shl 16) or
                ((data[offset + 2].toLong() and 0xFF) shl 8) or
                (data[offset + 3].toLong() and 0xFF)
        }

        fun tag(offset: Int): String {
            if (offset < 0 || offset + 4 > data.size) return ""
            return String(data, offset, 4, StandardCharsets.US_ASCII)
        }

        fun slice(offset: Int, length: Int): ByteArray {
            if (offset < 0 || length <= 0 || offset + length > data.size) return ByteArray(0)
            return data.copyOfRange(offset, offset + length)
        }
    }
}
