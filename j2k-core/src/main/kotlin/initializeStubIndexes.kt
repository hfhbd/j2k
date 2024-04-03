package com.intellij.psi.stubs

internal fun initializeStubIndexes() {
    (StubIndex.getInstance() as StubIndexEx).initializeStubIndexes()
}
