package com.spam.tasksandwich

/**
 * A single, central repository for all icon resources in the app.
 * This prevents code duplication and ensures consistency.
 */
object IconRepository {

    const val ADMIN_ICON_1 = "icon_admin_1"
    const val ADMIN_ICON_2 = "icon_admin_2"
    const val ADMIN_ICON_3 = "icon_admin_3"
    const val ADMIN_ICON_4 = "icon_admin_4"


    // do the same if you have teachers or mods ^^

    // 1. The single source of truth for all icons.
    val AllIconsMap = mapOf(
        // Default Icons
        "avatar_1" to R.drawable.carrotdog,
        "avatar_2" to R.drawable.dallebabyface,
        "avatar_3" to R.drawable.fglasses,
        "avatar_4" to R.drawable.firehairguy,
        "avatar_5" to R.drawable.vgfbhbluehair,

        // Milestone Icons
        "avatar_milestone_10" to R.drawable.bluehairguy,
        "avatar_milestone_25" to R.drawable.fzombie,
        "avatar_milestone_35" to R.drawable.women32,
        "avatar_milestone_50" to R.drawable.guywithglasses,
        "avatar_milestone_100" to R.drawable.handimg,
        "avatar_milestone_122" to R.drawable.zombiefunny,
        "avatar_milestone_250" to R.drawable.pixelimg,
        "avatar_milestone_310" to R.drawable.zombieimg,
        "avatar_milestone_465" to R.drawable.zombieboss,
        "avatar_milestone_666" to R.drawable.bluehaircartoon,
        "avatar_milestone_999" to R.drawable.thorimg,








        //Admin only
        ADMIN_ICON_1 to R.drawable.admin6000,
        ADMIN_ICON_2 to R.drawable.adminimg,
        ADMIN_ICON_3 to R.drawable.spamimg,
        ADMIN_ICON_4 to R.drawable.adminadmin,



    )

    // 2. The single source of truth for which icons are defaults.
    val DefaultIconIds = listOf(
        "avatar_1", "avatar_2", "avatar_3", "avatar_4", "avatar_5"
    )

    // 3. The single source of truth for milestone definitions.
    val MilestoneIconsMap = mapOf(
        "avatar_milestone_10" to 10,
        "avatar_milestone_25" to 25,
        "avatar_milestone_50" to 50
    )

    val RoleIconsMap = mapOf(
        "super_admin" to listOf(
            ADMIN_ICON_1,
            ADMIN_ICON_2,
            ADMIN_ICON_3,
            ADMIN_ICON_4,

        ),
        "teacher" to listOf()

    )
}