package com.aggin.carcost.data.reference

import com.aggin.carcost.data.local.database.entities.VehicleType

/**
 * Справочник марок и моделей.
 *
 * Марку и модель до сих пор набирали руками целиком, каждый по-своему: «ВАЗ»,
 * «Lada», «Лада», «лада». Для самого владельца это мелочь, но по такому полю
 * ничего нельзя ни сгруппировать, ни сравнить, а на телефоне лишние десять
 * символов — это лишние десять шансов бросить заполнение на полпути.
 *
 * Список зашит в приложение, а не тянется с сервера: он нужен в первую же
 * секунду на экране добавления, в том числе без сети, и меняется он раз в год.
 *
 * Подбор понимает и латиницу, и кириллицу: «тойота» находит Toyota. Выбор он не
 * ограничивает — своё название всегда можно дописать руками, подсказка лишь
 * предлагает.
 */
object VehicleCatalog {

    /** Марка: как пишется и как её ищут */
    private data class Brand(
        val name: String,
        val aliases: List<String> = emptyList(),
        val models: List<String> = emptyList()
    )

    // ── Автомобили ───────────────────────────────────────────────────────────
    private val cars = listOf(
        Brand("LADA", listOf("лада", "ваз", "vaz"), listOf(
            "Granta", "Vesta", "Largus", "Niva Legend", "Niva Travel", "XRAY",
            "Kalina", "Priora", "Samara", "2107", "2110", "2114", "2115", "4x4"
        )),
        Brand("Toyota", listOf("тойота"), listOf(
            "Camry", "Corolla", "RAV4", "Land Cruiser", "Land Cruiser Prado",
            "Highlander", "Avensis", "Yaris", "Auris", "Hilux", "Fortuner",
            "C-HR", "Venza", "Verso", "Prius", "Alphard", "Vitz", "Mark II"
        )),
        Brand("Kia", listOf("киа", "кия"), listOf(
            "Rio", "Sportage", "Ceed", "Cerato", "Optima", "K5", "Sorento",
            "Seltos", "Soul", "Picanto", "Carnival", "Mohave", "Stinger", "Spectra"
        )),
        Brand("Hyundai", listOf("хендай", "хёндэ", "хундай"), listOf(
            "Solaris", "Creta", "Tucson", "Santa Fe", "Elantra", "Sonata",
            "Accent", "Getz", "i30", "i40", "ix35", "Palisade", "Starex"
        )),
        Brand("Volkswagen", listOf("фольксваген", "vw"), listOf(
            "Polo", "Golf", "Passat", "Tiguan", "Touareg", "Jetta", "Teramont",
            "Caddy", "Transporter", "Multivan", "Touran", "Sharan", "Amarok"
        )),
        Brand("Skoda", listOf("шкода"), listOf(
            "Octavia", "Rapid", "Kodiaq", "Karoq", "Superb", "Fabia", "Yeti", "Kamiq"
        )),
        Brand("Renault", listOf("рено"), listOf(
            "Logan", "Sandero", "Duster", "Kaptur", "Arkana", "Megane",
            "Fluence", "Koleos", "Symbol", "Scenic", "Master"
        )),
        Brand("Nissan", listOf("ниссан"), listOf(
            "Qashqai", "X-Trail", "Almera", "Juke", "Murano", "Note", "Teana",
            "Terrano", "Patrol", "Pathfinder", "Tiida", "Primera", "Navara", "Leaf"
        )),
        Brand("Chery", listOf("чери", "черри"), listOf(
            "Tiggo 4", "Tiggo 4 Pro", "Tiggo 7 Pro", "Tiggo 8 Pro",
            "Tiggo 8 Pro Max", "Arrizo 8", "Tiggo 2", "Amulet", "Bonus"
        )),
        Brand("Haval", listOf("хавал", "хавейл"), listOf(
            "Jolion", "F7", "F7x", "Dargo", "H9", "H3", "M6", "H5"
        )),
        Brand("Geely", listOf("джили"), listOf(
            "Coolray", "Atlas", "Atlas Pro", "Monjaro", "Tugella", "Emgrand",
            "Okavango", "Preface", "Cityray"
        )),
        Brand("Changan", listOf("чанган"), listOf(
            "CS35 Plus", "CS55 Plus", "CS75 Plus", "UNI-K", "UNI-V", "UNI-T",
            "Alsvin", "Hunter"
        )),
        Brand("Exeed", listOf("эксид"), listOf("TXL", "LX", "VX", "RX")),
        Brand("Omoda", listOf("омода"), listOf("C5", "S5", "S5 GT", "C7")),
        Brand("Jetour", listOf("джетур"), listOf("Dashing", "X70 Plus", "X90 Plus", "T2")),
        Brand("Moskvich", listOf("москвич"), listOf("3", "3e", "6", "8", "2141", "412")),
        Brand("UAZ", listOf("уаз"), listOf(
            "Patriot", "Hunter", "Pickup", "Profi", "Буханка", "469"
        )),
        Brand("GAZ", listOf("газ"), listOf(
            "Газель Next", "Газель Business", "Соболь", "Волга", "3110"
        )),
        Brand("BMW", listOf("бмв"), listOf(
            "1 серия", "3 серия", "5 серия", "7 серия", "X1", "X3", "X4",
            "X5", "X6", "X7", "M3", "M5", "i3", "iX"
        )),
        Brand("Mercedes-Benz", listOf("мерседес", "мерс"), listOf(
            "A-Class", "C-Class", "E-Class", "S-Class", "GLA", "GLB", "GLC",
            "GLE", "GLS", "G-Class", "V-Class", "Sprinter", "Vito"
        )),
        Brand("Audi", listOf("ауди"), listOf(
            "A3", "A4", "A5", "A6", "A7", "A8", "Q3", "Q5", "Q7", "Q8", "TT", "e-tron"
        )),
        Brand("Mazda", listOf("мазда"), listOf(
            "3", "6", "CX-5", "CX-30", "CX-9", "CX-7", "2", "MX-5", "Demio"
        )),
        Brand("Mitsubishi", listOf("митсубиси", "мицубиси"), listOf(
            "Outlander", "Lancer", "Pajero", "Pajero Sport", "ASX", "L200",
            "Eclipse Cross", "Colt"
        )),
        Brand("Ford", listOf("форд"), listOf(
            "Focus", "Mondeo", "Kuga", "Explorer", "EcoSport", "Fiesta",
            "Transit", "Ranger", "Escape", "Fusion"
        )),
        Brand("Chevrolet", listOf("шевроле"), listOf(
            "Niva", "Cruze", "Aveo", "Lacetti", "Captiva", "Cobalt", "Camaro",
            "Tahoe", "Spark"
        )),
        Brand("Opel", listOf("опель"), listOf(
            "Astra", "Insignia", "Corsa", "Zafira", "Mokka", "Vectra", "Antara"
        )),
        Brand("Peugeot", listOf("пежо"), listOf(
            "308", "408", "3008", "5008", "206", "207", "Partner", "Boxer"
        )),
        Brand("Citroen", listOf("ситроен"), listOf(
            "C4", "C5", "C3", "Berlingo", "Jumper", "C-Elysee"
        )),
        Brand("Honda", listOf("хонда"), listOf(
            "Civic", "Accord", "CR-V", "Pilot", "Fit", "HR-V", "Jazz", "Stepwgn"
        )),
        Brand("Subaru", listOf("субару"), listOf(
            "Forester", "Outback", "XV", "Impreza", "Legacy", "Tribeca", "WRX"
        )),
        Brand("Suzuki", listOf("сузуки", "судзуки"), listOf(
            "Grand Vitara", "SX4", "Vitara", "Jimny", "Swift", "Escudo"
        )),
        Brand("Lexus", listOf("лексус"), listOf("RX", "NX", "LX", "GX", "ES", "IS", "LS", "UX", "GS")),
        Brand("Infiniti", listOf("инфинити"), listOf("QX50", "QX60", "QX70", "QX80", "FX35", "FX37", "Q50")),
        Brand("Volvo", listOf("вольво"), listOf("XC60", "XC90", "XC40", "S60", "S80", "S90", "V60", "V90")),
        Brand("Land Rover", listOf("ленд ровер", "лендровер"), listOf(
            "Range Rover", "Range Rover Sport", "Range Rover Velar",
            "Range Rover Evoque", "Discovery", "Discovery Sport", "Defender", "Freelander"
        )),
        Brand("Jeep", listOf("джип"), listOf("Grand Cherokee", "Cherokee", "Wrangler", "Compass", "Renegade")),
        Brand("Porsche", listOf("порше"), listOf("Cayenne", "Macan", "Panamera", "911", "Taycan", "Boxster")),
        Brand("Mini", listOf("мини"), listOf("Cooper", "Countryman", "Clubman", "Cooper S")),
        Brand("Fiat", listOf("фиат"), listOf("Ducato", "Doblo", "500", "Punto", "Albea", "Bravo")),
        Brand("SEAT", listOf("сеат"), listOf("Leon", "Ibiza", "Ateca", "Toledo")),
        Brand("Datsun", listOf("датсун"), listOf("on-DO", "mi-DO")),
        Brand("SsangYong", listOf("санг йонг", "саньенг"), listOf("Actyon", "Kyron", "Rexton", "Korando")),
        Brand("Daewoo", listOf("дэу"), listOf("Nexia", "Matiz", "Gentra", "Lanos")),
        Brand("Great Wall", listOf("грейт волл"), listOf("Hover", "Wingle", "Poer", "Safe")),
        Brand("BYD", listOf("бид"), listOf("Han", "Song Plus", "Seal", "Dolphin", "Atto 3", "Tang")),
        Brand("Zeekr", listOf("зикр"), listOf("001", "007", "X", "009")),
        Brand("Li Auto", listOf("лисян", "ли авто"), listOf("L7", "L8", "L9", "Mega")),
        Brand("Tank", listOf("танк"), listOf("300", "500", "700")),
        Brand("Voyah", listOf("воях"), listOf("Free", "Dream", "Passion")),
        Brand("Belgee", listOf("белджи"), listOf("X50", "X70", "S50")),
        Brand("Solaris", listOf("солярис"), listOf("HS", "KRS", "KRX")),
        Brand("Tesla", listOf("тесла"), listOf("Model 3", "Model Y", "Model S", "Model X", "Cybertruck")),
        Brand("Dongfeng", listOf("дунфэн"), listOf("Shine Max", "AX7", "580", "H30")),
        Brand("JAC", listOf("джак"), listOf("J7", "JS3", "JS6", "T6", "T8")),
        Brand("FAW", listOf("фав"), listOf("Bestune T77", "Bestune T99", "Besturn X40", "V5")),
        Brand("Lifan", listOf("лифан"), listOf("X60", "X50", "Solano", "Smily", "Myway")),
        Brand("Cadillac", listOf("кадиллак"), listOf("Escalade", "CTS", "SRX", "XT5")),
        Brand("Chrysler", listOf("крайслер"), listOf("300C", "Voyager", "Sebring", "PT Cruiser")),
        Brand("Dodge", listOf("додж"), listOf("Ram", "Journey", "Caliber", "Challenger")),
        Brand("Jaguar", listOf("ягуар"), listOf("XF", "XE", "F-Pace", "E-Pace")),
        Brand("Genesis", listOf("генезис"), listOf("G70", "G80", "G90", "GV70", "GV80")),
        Brand("Acura", listOf("акура"), listOf("MDX", "RDX", "TLX")),
        Brand("Iveco", listOf("ивеко"), listOf("Daily")),
        Brand("Isuzu", listOf("исузу"), listOf("D-Max", "NQR", "Elf"))
    )

    // ── Мотоциклы ────────────────────────────────────────────────────────────
    private val motorcycles = listOf(
        Brand("Honda", listOf("хонда"), listOf(
            "CB500X", "CB650R", "CBR600RR", "CBR1000RR", "Africa Twin",
            "Rebel 500", "NC750X", "Gold Wing", "CRF250L", "X-ADV", "Forza 350"
        )),
        Brand("Yamaha", listOf("ямаха"), listOf(
            "MT-07", "MT-09", "MT-03", "YZF-R1", "YZF-R6", "Tenere 700",
            "Tracer 900", "XSR700", "FZ6", "Drag Star", "TMAX", "WR250R"
        )),
        Brand("Suzuki", listOf("сузуки"), listOf(
            "GSX-R600", "GSX-R750", "GSX-R1000", "V-Strom 650", "V-Strom 1050",
            "SV650", "Bandit 650", "Boulevard M109R", "DR-Z400", "Hayabusa"
        )),
        Brand("Kawasaki", listOf("кавасаки"), listOf(
            "Ninja 400", "Ninja ZX-6R", "Ninja ZX-10R", "Z650", "Z900",
            "Versys 650", "Vulcan S", "KLX250", "W800"
        )),
        Brand("BMW", listOf("бмв"), listOf(
            "R 1250 GS", "R 1200 GS", "F 850 GS", "F 750 GS", "S 1000 RR",
            "R nineT", "G 310 R", "K 1600 GTL"
        )),
        Brand("KTM", listOf("ктм"), listOf(
            "Duke 390", "Duke 790", "Duke 1290", "RC 390", "Adventure 890",
            "EXC 300", "SX-F 450"
        )),
        Brand("Ducati", listOf("дукати"), listOf(
            "Monster", "Panigale V4", "Panigale V2", "Multistrada V4",
            "Scrambler", "Diavel", "Streetfighter"
        )),
        Brand("Harley-Davidson", listOf("харлей", "харли"), listOf(
            "Sportster 883", "Sportster 1200", "Street Bob", "Fat Boy",
            "Road King", "Street Glide", "Iron 883", "Pan America"
        )),
        Brand("Triumph", listOf("триумф"), listOf(
            "Street Triple", "Speed Triple", "Bonneville T120", "Tiger 900",
            "Rocket 3", "Trident 660"
        )),
        Brand("Aprilia", listOf("априлия"), listOf("RS 660", "Tuono 660", "RSV4", "Shiver 900")),
        Brand("Benelli", listOf("бенелли"), listOf("TRK 502", "TRK 702", "Leoncino 500", "302S")),
        Brand("CFMOTO", listOf("сфмото"), listOf("650NK", "650MT", "700CL-X", "800MT", "450SR", "300NK")),
        Brand("Voge", listOf("воге"), listOf("300R", "500R", "525DSX", "650DS", "900DS")),
        Brand("Royal Enfield", listOf("роял энфилд"), listOf(
            "Classic 350", "Meteor 350", "Himalayan", "Interceptor 650", "Continental GT 650"
        )),
        Brand("Bajaj", listOf("баджадж"), listOf("Pulsar NS200", "Dominar 400", "Avenger")),
        Brand("Ural", listOf("урал"), listOf("Gear Up", "Ranger", "М-72", "Solo sT", "Волк")),
        Brand("IZH", listOf("иж"), listOf("Планета 5", "Юпитер 5", "Ш-350")),
        Brand("Minsk", listOf("минск"), listOf("C4 250", "X250", "D4 125", "SCR 250")),
        Brand("Racer", listOf("рейсер"), listOf("Nitro", "Panther", "Ranger", "Skyway", "Tourist")),
        Brand("Regulmoto", listOf("регулмото"), listOf("Sport-003", "ZR", "Aqua", "Athlete")),
        Brand("Motoland", listOf("мотоленд"), listOf("XR250", "Bison", "Enduro", "GL200", "CT200")),
        Brand("Kayo", listOf("кайо"), listOf("T2", "T4", "K2", "K6", "TT125")),
        Brand("Stels", listOf("стелс"), listOf("Flex 250", "Trigger 250", "600 Benelli")),
        Brand("Avantis", listOf("авантис"), listOf("Enduro 250", "A7", "FX 250", "MT250")),
        Brand("Progasi", listOf("прогаси"), listOf("Palma 250", "Super Max", "Mudjet")),
        Brand("Jawa", listOf("ява"), listOf("350", "638", "42 FJ")),
        Brand("Husqvarna", listOf("хускварна"), listOf("Svartpilen 401", "Vitpilen 401", "TE 300", "FE 350")),
        Brand("Moto Guzzi", listOf("мото гуцци"), listOf("V7", "V85 TT", "V9")),
        Brand("Zontes", listOf("зонтес"), listOf("310T", "350R", "703F")),
        Brand("QJMotor", listOf("кюджей"), listOf("SRK 400", "SRV 550", "SRT 700"))
    )

    private fun catalogue(type: VehicleType) =
        if (type == VehicleType.MOTORCYCLE) motorcycles else cars

    /** Все марки — для показа списком, пока поле пустое */
    fun brands(type: VehicleType): List<String> = catalogue(type).map { it.name }

    /** Модели марки. Пустой список означает «подсказать нечем», а не «таких нет» */
    fun models(type: VehicleType, brand: String): List<String> =
        catalogue(type).firstOrNull { it.name.equals(brand, ignoreCase = true) }?.models ?: emptyList()

    /**
     * Подсказки марок по началу ввода.
     *
     * Сначала те, что начинаются с набранного, потом те, где оно встречается
     * дальше: «ро» должно первым делом привести к Royal Enfield, а не вывалить
     * всё, где попадаются эти буквы.
     */
    fun suggestBrands(type: VehicleType, query: String, limit: Int = 6): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val starts = catalogue(type).filter { brand ->
            brand.name.lowercase().startsWith(q) || brand.aliases.any { it.startsWith(q) }
        }
        val contains = catalogue(type).filter { brand ->
            brand !in starts &&
                (brand.name.lowercase().contains(q) || brand.aliases.any { it.contains(q) })
        }
        return (starts + contains).map { it.name }.take(limit)
    }

    /** Подсказки моделей. Пока поле пустое — первые из списка марки */
    fun suggestModels(type: VehicleType, brand: String, query: String, limit: Int = 6): List<String> {
        val all = models(type, brand)
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all.take(limit)
        val starts = all.filter { it.lowercase().startsWith(q) }
        val contains = all.filter { it !in starts && it.lowercase().contains(q) }
        return (starts + contains).take(limit)
    }
}
