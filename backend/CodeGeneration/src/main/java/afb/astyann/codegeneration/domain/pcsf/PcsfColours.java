package afb.astyann.codegeneration.domain.pcsf;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * Full Angular Material colour palette for the generated frontend.
 * Defaults to the Afriland First Bank brand palette.
 */
@Data
public class PcsfColours {
    // Brand colours
    private String primary    = "#CC0000";
    private String secondary  = "#FFFFFF";
    private String accent     = "#FF6600";

    // Surface / background
    @JsonAlias({"bg", "backgroundColor"})
    private String background = "#F5F5F5";
    private String surface    = "#FFFFFF";

    // Semantic colours
    private String error      = "#B00020";
    private String warning    = "#FF9800";
    private String success    = "#4CAF50";
    private String info       = "#1976D2";

    // Text
    private String neutral    = "#6B6B6B";
    private String text       = "#1A1A1A";
    private String textOnPrimary = "#FFFFFF";
}
