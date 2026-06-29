package afb.astyann.requirementservice.util;

import afb.astyann.requirementservice.domain.pcsf.*;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldSource;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PcsfFieldWriter {

    public void write(Pcsf pcsf, String path, String value) {
        if (path == null || value == null) return;
        try {
            switch (path) {
                case "project.name.value"        -> setString(pcsf.getProject().getName(), value, pcsf);
                case "project.description.value" -> setString(pcsf.getProject().getDescription(), value, pcsf);
                case "project.displayName.value" -> setString(pcsf.getProject().getDisplayName(), value, pcsf);
                case "actors"                    -> pcsf.setActors(parseActors(value));
                case "modules"                   -> pcsf.setModules(parseModules(value));
                case "conditionalFeatures.fileUpload.required.value"   ->
                        setFlag(pcsf.getConditionalFeatures().getFileUpload(), value);
                case "conditionalFeatures.dataExport.required.value"   ->
                        setFlag(pcsf.getConditionalFeatures().getDataExport(), value);
                case "conditionalFeatures.searchFilter.required.value" ->
                        setFlag(pcsf.getConditionalFeatures().getSearchFilter(), value);
                case "conditionalFeatures.multiTenancy.required.value" ->
                        setFlag(pcsf.getConditionalFeatures().getMultiTenancy(), value);
                default -> log.warn("Unknown PCSF path: {}", path);
            }
        } catch (Exception ex) {
            log.error("Failed to write PCSF path={} value={}", path, value, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private void setString(FieldValue<?> fv, String value, Pcsf pcsf) {
        if (fv == null) return;
        ((FieldValue<String>) fv).setValue(value);
        fv.setSource(FieldSource.QA);
        fv.setStatus(FieldStatus.CONFIRMED);
    }

    private void setFlag(ConditionalFlag flag, String value) {
        if (flag == null) return;
        boolean boolVal = value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true")
                || value.equals("1");
        flag.setTriggered(boolVal);
        if (flag.getRequired() == null) {
            flag.setRequired(new FieldValue<>());
        }
        flag.getRequired().setValue(boolVal);
        flag.getRequired().setSource(FieldSource.QA);
        flag.getRequired().setStatus(FieldStatus.CONFIRMED);
    }

    public List<PcsfActor> parseActors(String answer) {
        AtomicInteger counter = new AtomicInteger(1);
        return Arrays.stream(answer.split("\n"))
                .map(String::trim)
                .filter(line -> line.contains("|"))
                .map(line -> {
                    String[] parts = line.split("\\|");
                    if (parts.length < 3) return null;
                    String typeVal = parts[1].trim().toUpperCase().contains("EXTERNAL")
                            ? "EXTERNAL" : "INTERNAL";
                    return PcsfActor.builder()
                            .id(String.format("ACT-%02d", counter.getAndIncrement()))
                            .name(fv(parts[0].trim()))
                            .type(fv(typeVal))
                            .description(fv(parts[2].trim()))
                            .build();
                })
                .filter(a -> a != null)
                .collect(Collectors.toList());
    }

    public List<PcsfModule> parseModules(String answer) {
        AtomicInteger counter = new AtomicInteger(1);
        return Arrays.stream(answer.split("\n"))
                .map(String::trim)
                .filter(line -> line.contains("|"))
                .map(line -> {
                    String[] parts = line.split("\\|");
                    if (parts.length < 2) return null;
                    return PcsfModule.builder()
                            .id(String.format("MOD-%02d", counter.getAndIncrement()))
                            .name(fv(parts[0].trim()))
                            .description(fv(parts[1].trim()))
                            .crudOperations(FieldValue.<List<String>>builder()
                                    .value(new ArrayList<>(List.of("CREATE", "READ", "UPDATE", "DELETE")))
                                    .source(FieldSource.DEFAULT)
                                    .status(FieldStatus.CONFIRMED)
                                    .build())
                            .useCases(new ArrayList<>())
                            .build();
                })
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }

    private FieldValue<String> fv(String value) {
        return FieldValue.<String>builder()
                .value(value)
                .source(FieldSource.QA)
                .status(FieldStatus.CONFIRMED)
                .build();
    }
}
