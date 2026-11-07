# Bot X (mod Fabric, Minecraft 1.21.10, tylko klient)

## Jak zbudować
1. Zainstaluj JDK 21 i Gradle (albo wygeneruj wrapper: `gradle wrapper`).
2. W folderze moda: `gradle build`
3. Gotowy plik: `build/libs/botx-0.1.0.jar` -> wrzuć do `.minecraft/mods`
4. Potrzebujesz: Fabric Loader + Fabric API dla tej samej wersji Minecrafta.

Wersje w `gradle.properties` (minecraft_version, loader_version, loom_version, fabric_version)
sprawdź na https://fabricmc.net/develop i dopasuj do swojego Minecrafta.
Mod nie był jeszcze kompilowany - jeśli Gradle zgłosi błąd nazwy klasy/metody, wyślij mi go, poprawię.

## Użycie
- **N** - menu Bot X (zmiana klawisza: Opcje > Sterowanie > Bot X)
- **Zdobądź przedmiot** - zakładki Bloki / Przedmioty, wybierasz cel i ilość, zielony **START**
  - bot sam: ścina drewno, robi deski/patyki/stół, kilof drewniany -> kamienny -> żelazny (kopie rudę,
    stawia piec, przetapia), potem diamenty
- **Zabij gracza** - wpisz nick, bot biegnie do gracza i go bije (gracz musi być w zasięgu renderowania)
- **Przejdź Minecrafta (etap 1)** - zestaw: kilofy do żelaznego, miecz żelazny, diamentowy kilof i miecz.
  Nether i Smok Endu NIE są jeszcze zaimplementowane.
- **Wyłącz bota** - zatrzymuje wszystko (działa też gdy bot zginie / ma mało zdrowia / trafi na lawę)

## Uwaga
Automatyczne boty są zabronione na wielu serwerach - używaj w singleplayerze lub tam, gdzie wolno.
