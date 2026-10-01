package app.corkboard.data

/** A category as the site's front page lists it: a name and the abbreviation searches use. */
data class Category(val abbr: String, val name: String)

data class Section(val name: String, val all: Category, val categories: List<Category>)

/**
 * The front page's category list. It has barely changed in twenty years, and it is the one piece
 * of the site with no JSON behind it, so it lives here. What each category can be filtered by is
 * not here: the site sends that with every search.
 */
object Catalog {
    private fun c(abbr: String, name: String) = Category(abbr, name)

    val forSale = Section(
        "For sale", c("sss", "All for sale"),
        listOf(
            c("cta", "Cars & trucks"), c("zip", "Free stuff"), c("sya", "Computers"), c("ela", "Electronics"),
            c("fua", "Furniture"), c("ppa", "Appliances"), c("moa", "Cell phones"), c("syp", "Computer parts"),
            c("tla", "Tools"), c("bia", "Bikes"), c("bip", "Bike parts"), c("mca", "Motorcycles"),
            c("mpa", "Motorcycle parts"), c("pta", "Auto parts"), c("wta", "Wheels & tires"), c("vga", "Video gaming"),
            c("msa", "Musical instruments"), c("pha", "Photo & video"), c("sga", "Sporting goods"), c("hsa", "Household"),
            c("gra", "Farm & garden"), c("maa", "Materials"), c("ata", "Antiques"), c("ara", "Arts & crafts"),
            c("sna", "ATVs, UTVs, snowmobiles"), c("ava", "Aviation"), c("baa", "Baby & kid"), c("bar", "Barter"),
            c("haa", "Health & beauty"), c("boo", "Boats"), c("bpa", "Boat parts"), c("bka", "Books"),
            c("bfa", "Business"), c("ema", "CDs, DVDs, VHS"), c("cla", "Clothing & accessories"), c("cba", "Collectibles"),
            c("gms", "Garage sales"), c("foa", "General"), c("hva", "Heavy equipment"), c("jwa", "Jewelry"),
            c("rva", "RVs & campers"), c("tia", "Tickets"), c("taa", "Toys & games"), c("tra", "Trailers"),
            c("waa", "Wanted"),
        ),
    )

    val housing = Section(
        "Housing", c("hhh", "All housing"),
        listOf(
            c("apa", "Apartments for rent"), c("roo", "Rooms & shares"), c("sub", "Sublets & temporary"),
            c("rea", "Real estate for sale"), c("vac", "Vacation rentals"), c("prk", "Parking & storage"),
            c("off", "Office & commercial"), c("swp", "Housing swap"), c("hsw", "Housing wanted"), c("sha", "Rooms wanted"),
        ),
    )

    val more = Section(
        "More", c("sss", "All for sale"),
        listOf(c("jjj", "Jobs"), c("ggg", "Gigs"), c("bbb", "Services"), c("ccc", "Community"), c("eee", "Events")),
    )

    val sections = listOf(forSale, housing, more)

    /** The handful shown as large tiles at the top of the home screen. */
    val featured = listOf("cta", "zip", "sya", "ela", "fua", "apa").mapNotNull { abbr -> sections.flatMap { it.categories }.firstOrNull { it.abbr == abbr } }
}
