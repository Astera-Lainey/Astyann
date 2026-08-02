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

    private ${entity.idType} id;
<#list entity.fields as field>
    private ${field.javaType} ${field.name};
</#list>
<#if entity.audited>
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
</#if>
}
