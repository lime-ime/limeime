struct TouchTracker: Equatable {
    let downKey: KeyModel?
    var currentKey: KeyModel?
    let isModifier: Bool

    init(downKey: KeyModel?, currentKey: KeyModel? = nil) {
        self.downKey = downKey
        self.currentKey = currentKey ?? downKey
        self.isModifier = downKey?.isModifier ?? false
    }
}
