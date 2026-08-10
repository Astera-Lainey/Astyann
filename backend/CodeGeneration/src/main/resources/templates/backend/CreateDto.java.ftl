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
<#-- Must stay the same predicate ServiceImpl's applyValues uses, or it calls a getter this DTO
     does not declare. See BackendField.serverManaged. -->
<#if !field.serverManaged>
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
