package io.github.raghavsatyadev.jobfinder.shared.backup

import dev.whyoleg.cryptography.CryptographyProvider

/** CryptoKit for AES-GCM and CommonCrypto for PBKDF2, through the "optimal" provider. */
internal actual val backupCryptoProvider: CryptographyProvider
  get() = CryptographyProvider.Default
