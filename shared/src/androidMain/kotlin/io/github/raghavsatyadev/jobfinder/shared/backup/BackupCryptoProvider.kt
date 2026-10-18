package io.github.raghavsatyadev.jobfinder.shared.backup

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK

internal actual val backupCryptoProvider: CryptographyProvider
  get() = CryptographyProvider.JDK
