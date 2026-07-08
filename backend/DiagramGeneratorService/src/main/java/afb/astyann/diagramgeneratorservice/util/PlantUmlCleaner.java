package afb.astyann.diagramgeneratorservice.util;

public final class PlantUmlCleaner {

    private PlantUmlCleaner() {}

    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("(?s)```plantuml\\s*", "")
                      .replaceAll("(?s)```uml\\s*", "")
                      .replaceAll("(?s)```\\s*", "")
                      .trim();
        int start = s.indexOf("@startuml");
        int end = s.lastIndexOf("@enduml");
        if (start >= 0 && end > start) {
            s = s.substring(start, end + "@enduml".length());
        }
        return s;
    }
}
