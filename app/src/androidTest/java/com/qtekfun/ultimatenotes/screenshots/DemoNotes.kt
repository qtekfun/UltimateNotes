// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.screenshots

import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/** What the screenshots show for one language: made-up notes and the text to type and tap. */
class DemoContent(
    val notes: List<DemoNote>,
    /** Title of the note opened in the editor screenshot. */
    val editorTitle: String,
    /** What is typed in the search screenshot. */
    val query: String
)

/** A made-up note, [age] before the moment the screenshots are taken. */
class DemoNote(
    val title: String,
    val category: String,
    val content: String,
    val age: Duration,
    val favorite: Boolean = false
) {
    fun toEntity(clock: Clock) = NoteEntity(
        modified = clock.instant().minus(age).epochSecond,
        title = title,
        category = category,
        content = content,
        favorite = favorite,
        syncState = SyncState.SYNCED,
        lastSyncedEtag = "demo"
    )
}

/**
 * The generic demo notes (recipes, a trip, books, meetings): no real names or personal data.
 * Ages are relative so the list shows Pinned, Today, Yesterday and the older sections. A run
 * shortly after midnight would put the "today" notes on yesterday; run it later in the day.
 */
object DemoNotes {
    private const val YESTERDAY_HOUR = 15L

    private fun minutes(count: Long) = Duration.ofMinutes(count)

    private fun hours(count: Long) = Duration.ofHours(count)

    private fun days(count: Long) = Duration.ofDays(count)

    /** An age that lands yesterday at 15:00, whatever the time of day. */
    private fun yesterday(clock: Clock): Duration {
        val startOfToday = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant()
        val target = startOfToday.minus(hours(24 - YESTERDAY_HOUR))
        return Duration.between(target, clock.instant())
    }

    fun of(language: String, clock: Clock): DemoContent {
        val yesterday = yesterday(clock)
        return if (language == "es") spanish(yesterday) else english(yesterday)
    }

    @Suppress("LongMethod")
    private fun english(yesterday: Duration) = DemoContent(
        editorTitle = "Lisbon trip plan",
        query = "lemon",
        notes = listOf(
            DemoNote(
                "Lisbon trip plan",
                "Travel",
                """
                |## Itinerary
                |
                |**Day 1** - arrive, then a slow walk through the old town. *Dinner by the river.*
                |
                |**Day 2** - tram to the castle, then a long lunch with a view.
                |
                |## Packing checklist
                |
                |- [x] Passport and tickets
                |- [x] Phone charger
                |- [x] Comfortable shoes
                |- [ ] Sunscreen
                |- [ ] Camera
                """.trimMargin(),
                minutes(40),
                favorite = true
            ),
            DemoNote(
                "Grocery list",
                "",
                "- [x] Oat milk\n- [ ] Lemons\n- [ ] Pasta\n- [ ] Basil\n- [ ] Coffee beans",
                hours(1),
                favorite = true
            ),
            DemoNote(
                "Team meeting",
                "Work",
                "## Agenda\n\nReview the roadmap and plan the next release.\n\n" +
                    "- [x] Share the notes\n- [ ] Book the next meeting",
                minutes(15)
            ),
            DemoNote(
                "Fluffy pancakes",
                "Recipes",
                "Whisk the eggs with the milk, fold in the flour and rest the batter. " +
                    "Serve with fruit.",
                minutes(5)
            ),
            DemoNote(
                "Reading list",
                "Reading",
                "- [x] A novel about the sea\n- [ ] A short history of bread\n- [ ] Poems",
                yesterday
            ),
            DemoNote(
                "Weekly goals",
                "Work",
                "Finish the draft, tidy the inbox and go for a run on Friday.",
                yesterday.plus(minutes(30))
            ),
            DemoNote(
                "Lemon cake",
                "Recipes/Desserts",
                "Zest of two lemons, 200 g sugar, 3 eggs and 150 g butter. " +
                    "Bake for 40 minutes and glaze with lemon juice.",
                days(3)
            ),
            DemoNote(
                "Weekend brunch ideas",
                "Recipes",
                "Avocado toast, yogurt with honey, fresh lemon juice and a pot of tea.",
                days(5)
            ),
            DemoNote(
                "Book ideas",
                "Reading",
                "A cookbook for small kitchens. A travel diary. A guide to slow mornings.",
                days(12)
            ),
            DemoNote(
                "Sourdough bread",
                "Recipes",
                "Feed the starter the night before. Mix, fold every 30 minutes, bake hot.",
                days(20)
            ),
            DemoNote(
                "Project kickoff",
                "Work",
                "Goals, owners and a first milestone for the new project.",
                days(60)
            )
        )
    )

    @Suppress("LongMethod")
    private fun spanish(yesterday: Duration) = DemoContent(
        editorTitle = "Plan del viaje a Lisboa",
        query = "limón",
        notes = listOf(
            DemoNote(
                "Plan del viaje a Lisboa",
                "Viajes",
                """
                |## Itinerario
                |
                |**Día 1** - llegada y paseo tranquilo por el casco antiguo. *Cena junto al río.*
                |
                |**Día 2** - tranvía hasta el castillo y una comida larga con vistas.
                |
                |## Lista de equipaje
                |
                |- [x] Pasaporte y billetes
                |- [x] Cargador del móvil
                |- [x] Zapatos cómodos
                |- [ ] Protector solar
                |- [ ] Cámara
                """.trimMargin(),
                minutes(40),
                favorite = true
            ),
            DemoNote(
                "Lista de la compra",
                "",
                "- [x] Bebida de avena\n- [ ] Limones\n- [ ] Pasta\n- [ ] Albahaca\n- [ ] Café",
                hours(1),
                favorite = true
            ),
            DemoNote(
                "Reunión de equipo",
                "Trabajo",
                "## Orden del día\n\nRevisar la hoja de ruta y planificar la próxima versión.\n\n" +
                    "- [x] Compartir las notas\n- [ ] Reservar la próxima reunión",
                minutes(15)
            ),
            DemoNote(
                "Tortitas esponjosas",
                "Recetas",
                "Bate los huevos con la leche, añade la harina y deja reposar la masa. " +
                    "Sirve con fruta.",
                minutes(5)
            ),
            DemoNote(
                "Lista de lecturas",
                "Lecturas",
                "- [x] Una novela sobre el mar\n- [ ] Una breve historia del pan\n- [ ] Poemas",
                yesterday
            ),
            DemoNote(
                "Objetivos de la semana",
                "Trabajo",
                "Terminar el borrador, ordenar el correo y salir a correr el viernes.",
                yesterday.plus(minutes(30))
            ),
            DemoNote(
                "Bizcocho de limón",
                "Recetas/Postres",
                "Ralladura de dos limones, 200 g de azúcar, 3 huevos y 150 g de mantequilla. " +
                    "Hornear 40 minutos y glasear con zumo de limón.",
                days(3)
            ),
            DemoNote(
                "Ideas para un brunch",
                "Recetas",
                "Tostada de aguacate, yogur con miel, zumo de limón recién hecho y té.",
                days(5)
            ),
            DemoNote(
                "Ideas de libros",
                "Lecturas",
                "Un libro de cocina para cocinas pequeñas. Un diario de viaje. " +
                    "Una guía de mañanas lentas.",
                days(12)
            ),
            DemoNote(
                "Pan de masa madre",
                "Recetas",
                "Alimenta la masa madre la noche anterior. Mezcla, pliega cada 30 minutos " +
                    "y hornea fuerte.",
                days(20)
            ),
            DemoNote(
                "Arranque del proyecto",
                "Trabajo",
                "Objetivos, responsables y un primer hito para el nuevo proyecto.",
                days(60)
            )
        )
    )
}
