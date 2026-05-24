import Foundation

// Static catalog of all downloadable input methods.
// Source: Android's SetupImLoadDialog.java button configs + LIME.java cloud URLs.
// Base URL: https://github.com/lime-ime/limeime/raw/master/Database/

// MARK: - Models

struct IMFamily: Identifiable, Hashable {
    let id: String           // unique key
    let chineseName: String  // e.g. "注音"
    let englishName: String  // e.g. "Phonetic"
    let description: String  // one-line description
    let systemIcon: String   // SF Symbol name
    let variants: [IMVariant]

    func hash(into hasher: inout Hasher) { hasher.combine(id) }
    static func == (l: IMFamily, r: IMFamily) -> Bool { l.id == r.id }
}

struct IMVariant: Identifiable, Hashable {
    let id: String           // unique key (also used as a stable identifier)
    let name: String         // display name, e.g. "標準"
    let filename: String     // file in Database/ folder (zip or limedb)
    let tableName: String    // SQLite table name to import into
    let imName: String       // im.im_name value
    let label: String        // im.label value
    let keyboardId: String   // im.keyboard_id
    let recordCount: Int     // approximate number of entries
    let compressedKB: Int    // approximate download size in KB

    func hash(into hasher: inout Hasher) { hasher.combine(id) }
    static func == (l: IMVariant, r: IMVariant) -> Bool { l.id == r.id }

    static let baseURL = "https://github.com/lime-ime/limeime/raw/master/Database/"

    var downloadURL: URL {
        URL(string: Self.baseURL + filename)!
    }

    var sizeString: String {
        compressedKB < 1024
            ? "\(compressedKB) KB"
            : String(format: "%.1f MB", Double(compressedKB) / 1024)
    }
}

// MARK: - Catalog

enum IMCatalog {

    static let families: [IMFamily] = [
        .init(
            id: "phonetic",
            chineseName: "注音",
            englishName: "Phonetic / BPMF",
            description: "標準注音符號，適合台灣使用者",
            systemIcon: "character.phonetic",
            variants: [
                .init(id: "phonetic",              name: "OpenVanilla 注音字根",          filename: "phonetic.zip",              tableName: "phonetic", imName: "phonetic",         label: "注音",      keyboardId: "phonetic",  recordCount: 34_838,  compressedKB: 589),
                .init(id: "phoneticbig5",          name: "OpenVanilla 注音字根 (BIG5字集)", filename: "phoneticbig5.zip",          tableName: "phonetic", imName: "phonetic",         label: "注音",      keyboardId: "phonetic",  recordCount: 15_945,  compressedKB: 465),
                .init(id: "phoneticcomplete",      name: "注音連打字根",                   filename: "phoneticcomplete.zip",      tableName: "phonetic", imName: "phonetic",         label: "注音",      keyboardId: "phonetic",  recordCount: 95_029,  compressedKB: 2900),
                .init(id: "phoneticcompletebig5",  name: "注音連打字根 (BIG5字集)",         filename: "phoneticcompletebig5.zip",  tableName: "phonetic", imName: "phonetic",         label: "注音",      keyboardId: "phonetic",  recordCount: 76_122,  compressedKB: 2400),
            ]
        ),
        .init(
            id: "cj",
            chineseName: "倉頡",
            englishName: "Cangjie",
            description: "倉頡輸入法，多種字集選擇",
            systemIcon: "square.grid.2x2",
            variants: [
                .init(id: "cj",     name: "倉頡字根",           filename: "cj.zip",     tableName: "cj", imName: "cj", label: "倉頡五代", keyboardId: "cj", recordCount: 28_596, compressedKB: 830),
                .init(id: "cjbig5", name: "倉頡字根 (BIG5字集)", filename: "cjbig5.zip", tableName: "cj", imName: "cj", label: "倉頡五代", keyboardId: "cj", recordCount: 13_859, compressedKB: 506),
                .init(id: "cjhk",   name: "倉頡香港字字根",      filename: "cjhk.zip",   tableName: "cj", imName: "cj", label: "倉頡五代", keyboardId: "cj", recordCount: 30_278, compressedKB: 884),
            ]
        ),
        .init(
            id: "cj4",
            chineseName: "四碼倉頡",
            englishName: "Four-code Cangjie",
            description: "四碼倉頡輸入法",
            systemIcon: "square.grid.2x2",
            variants: [
                .init(id: "cj4", name: "哈哈倉頡", filename: "cj4.limedb", tableName: "cj4", imName: "cj4", label: "哈哈倉頡", keyboardId: "cj", recordCount: 33_021, compressedKB: 598),
            ]
        ),
        .init(
            id: "cj5",
            chineseName: "倉頡五代",
            englishName: "Cangjie 5",
            description: "倉頡五代輸入法",
            systemIcon: "square.grid.2x2",
            variants: [
                .init(id: "cj5", name: "倉頡五代字根", filename: "cj5.zip", tableName: "cj5", imName: "cj5", label: "倉頡五代", keyboardId: "cj", recordCount: 24_004, compressedKB: 491),
            ]
        ),
        .init(
            id: "scj",
            chineseName: "快倉",
            englishName: "Quick Cangjie",
            description: "快速倉頡輸入法",
            systemIcon: "square.grid.2x2",
            variants: [
                .init(id: "scj", name: "快倉字根", filename: "scj.zip", tableName: "scj", imName: "scj", label: "速成", keyboardId: "limenum", recordCount: 74_250, compressedKB: 1400),
            ]
        ),
        .init(
            id: "ecj",
            chineseName: "速成",
            englishName: "Easy Cangjie",
            description: "速成輸入法",
            systemIcon: "square.grid.2x2",
            variants: [
                .init(id: "ecj",   name: "簡易速成",       filename: "ecj.zip",   tableName: "ecj", imName: "ecj", label: "ECJ",    keyboardId: "cj", recordCount: 13_119, compressedKB: 136),
                .init(id: "ecjhk", name: "速成香港字字根", filename: "ecjhk.zip", tableName: "ecj", imName: "ecj", label: "ECJ HK", keyboardId: "cj", recordCount: 27_853, compressedKB: 210),
            ]
        ),
        .init(
            id: "dayi",
            chineseName: "大易",
            englishName: "Dayi",
            description: "大易輸入法，支援統一碼字集",
            systemIcon: "textformat.alt",
            variants: [
                .init(id: "dayi",     name: "OpenVanilla 大易字根",  filename: "dayi.zip",     tableName: "dayi", imName: "dayi",  label: "大易",     keyboardId: "dayisym", recordCount: 18_638,  compressedKB: 486),
                .init(id: "dayiuni",  name: "Unicode 3+4 碼單字版", filename: "dayiuni.zip",  tableName: "dayi", imName: "dayi",  label: "大易",     keyboardId: "dayisym", recordCount: 27_198,  compressedKB: 584),
                .init(id: "dayiunip", name: "Unicode 3+4 碼詞庫版", filename: "dayiunip.zip", tableName: "dayi", imName: "dayi",  label: "大易",     keyboardId: "dayisym", recordCount: 117_766, compressedKB: 2700),
            ]
        ),
        .init(
            id: "ez",
            chineseName: "輕鬆",
            englishName: "EZ",
            description: "輕鬆輸入法",
            systemIcon: "hand.tap",
            variants: [
                .init(id: "ez", name: "輕鬆字根", filename: "ez.limedb", tableName: "ez", imName: "ez", label: "輕鬆", keyboardId: "ez", recordCount: 14_422, compressedKB: 237),
            ]
        ),
        .init(
            id: "array",
            chineseName: "行列",
            englishName: "Array (行列30)",
            description: "行列輸入法 30 鍵標準版",
            systemIcon: "table",
            variants: [
                // Source: Android LIME.java DATABASE_CLOUD_IM_ARRAY.
                // The Android URL uses array.limedb but that file wraps a long Android
                // cache path (storage/emulated/0/Android/data/.../array.db) inside a zip.
                // array.zip in the same Database/ folder is a cleaner zip wrapping a bare
                // "array.db" — prefer that for the iOS unzip + attach pipeline.
                .init(id: "array", name: "老刀行列字根", filename: "array.limedb",
                      tableName: "array", imName: "array", label: "行列30",
                      keyboardId: "array", recordCount: 32_386, compressedKB: 524),
            ]
        ),
        .init(
            id: "array10",
            chineseName: "行列10",
            englishName: "Array 10 (行列10)",
            description: "行列輸入法 10 鍵版本 (電話鍵盤)",
            systemIcon: "table.badge.more",
            variants: [
                // Source: Android LIME.java DATABASE_CLOUD_IM_ARRAY10.
                // Android uses array10.limedb which wraps a long cache path; we use
                // array10.zip for the clean filename. Array and Array10 are separate
                // IMs with different tableName / imName (matches Android IM_ARRAY10).
                .init(id: "array10", name: "老刀行列10字根", filename: "array10.limedb",
                      tableName: "array10", imName: "array10", label: "行列10",
                      keyboardId: "phone_simple", recordCount: 32_120, compressedKB: 558),
            ]
        ),
        .init(
            id: "wb",
            chineseName: "筆順",
            englishName: "WB (Stroke)",
            description: "筆順五碼輸入法",
            systemIcon: "pencil.and.outline",
            variants: [
                .init(id: "wb", name: "筆順五碼字根", filename: "wb.zip", tableName: "wb", imName: "wb", label: "筆順", keyboardId: "wb", recordCount: 26_378, compressedKB: 267),
            ]
        ),
        .init(
            id: "hs",
            chineseName: "華象",
            englishName: "HS",
            description: "華象直覺輸入法，多種版本",
            systemIcon: "wand.and.stars",
            variants: [
                .init(id: "hs",  name: "華象完整版", filename: "hs.zip",  tableName: "hs", imName: "hs", label: "華象", keyboardId: "hs", recordCount: 183_659, compressedKB: 3600),
                .init(id: "hs1", name: "華象一版",  filename: "hs1.zip", tableName: "hs", imName: "hs", label: "華象", keyboardId: "hs", recordCount: 50_845,  compressedKB: 830),
                .init(id: "hs2", name: "華象二版",  filename: "hs2.zip", tableName: "hs", imName: "hs", label: "華象", keyboardId: "hs", recordCount: 50_838,  compressedKB: 834),
                .init(id: "hs3", name: "華象三版",  filename: "hs3.zip", tableName: "hs", imName: "hs", label: "華象", keyboardId: "hs", recordCount: 64_324,  compressedKB: 1000),
            ]
        ),
        .init(
            id: "pinyin",
            chineseName: "拼音",
            englishName: "Pinyin",
            description: "漢語拼音輸入法",
            systemIcon: "text.bubble",
            variants: [
                .init(id: "pinyin",   name: "拼音字根",         filename: "pinyin.zip",   tableName: "pinyin", imName: "pinyin", label: "拼音",      keyboardId: "pinyin", recordCount: 34_753, compressedKB: 509),
                .init(id: "pinyingb", name: "拼音字根 (簡體GB)", filename: "pinyingb.zip", tableName: "pinyin", imName: "pinyin", label: "拼音 (GB)", keyboardId: "pinyin", recordCount: 34_753, compressedKB: 502),
            ]
        ),
        // 自建輸入法 — no cloud download; local import only (§13.3)
        .init(
            id: "custom",
            chineseName: "自建",
            englishName: "Custom",
            description: "使用者自建輸入法，匯入 .limedb 或 .cin/.lime 檔案",
            systemIcon: "person.crop.rectangle",
            variants: []
        ),
    ]

    /// Flat list of all variants for search
    static var allVariants: [IMVariant] {
        families.flatMap { $0.variants }
    }
}
