// Prayer times on Home come from JAKIM's e-Solat, which divides Malaysia into zones with a code each:
// three letters for the state and two digits for the zone. The TV fetches the times itself, by zone.

/** A JAKIM zone code, as the launcher's HomeConfig accepts it. */
export const PRAYER_ZONE_RE = /^[A-Z]{3}[0-9]{2}$/

/** Sarawak's zones, for picking by town. Other states: type the code from e-solat.gov.my. */
export const SARAWAK_ZONES: readonly { code: string; places: string }[] = [
  { code: 'SWK01', places: 'Limbang, Lawas, Sundar, Trusan' },
  { code: 'SWK02', places: 'Miri, Niah, Bekenu, Sibuti, Marudi' },
  { code: 'SWK03', places: 'Bintulu, Belaga, Tatau, Sebauh, Suai, Pandan' },
  { code: 'SWK04', places: 'Sibu, Mukah, Dalat, Song, Kanowit, Kapit' },
  { code: 'SWK05', places: 'Sarikei, Matu, Julau, Bintangor, Belawai' },
  { code: 'SWK06', places: 'Sri Aman, Betong, Saratok, Lubok Antu, Kabong' },
  { code: 'SWK07', places: 'Serian, Samarahan, Simunjan, Sebuyau' },
  { code: 'SWK08', places: 'Kuching, Bau, Lundu, Sematan' },
  { code: 'SWK09', places: 'Zon Khas (Kampung Patarikan)' }
]
