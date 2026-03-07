package com.mirchevsky.lifearchitect2.domain

/**
 * Represents a single avatar part available in the Avatar Vault.
 *
 * @param id Unique identifier, 1–50. Also used to look up the drawable resource.
 * @param name The display name shown in the vault grid.
 * @param drawableRes The R.drawable resource ID for the avatar image.
 */
data class Avatar(
    val id: Int,
    val name: String,
    val drawableRes: Int
)
