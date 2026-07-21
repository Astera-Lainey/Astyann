package ${project.packageName}.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class Create${entity.className}Dto {

<#list entity.fields as field>
<#if field.name != "currentStock" && field.name != "stockStatus" && field.name != "status">
<#if field.required>
    @NotNull
</#if>
<#if (field.minLength)?? || (field.maxLength)??>
    @Size(<#if (field.minLength)??>min = ${field.minLength?c}<#if (field.maxLength)??>, </#if></#if><#if (field.maxLength)??>max = ${field.maxLength?c}</#if>)
</#if>
    private ${field.javaType} ${field.name};

</#if>
</#list>
}
