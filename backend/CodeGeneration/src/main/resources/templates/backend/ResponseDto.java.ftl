package ${project.packageName}.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class ${entity.className}ResponseDto {

<#if entity.idStrategy == "IDENTITY">
    private Long id;
<#else>
    private UUID id;
</#if>
<#list entity.fields as field>
    private ${field.javaType} ${field.name};
</#list>
<#if entity.audited>
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
</#if>
}
