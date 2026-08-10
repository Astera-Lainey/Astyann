package ${project.packageName}.service.impl;

import ${project.packageName}.dto.*;
import ${project.packageName}.entity.${module.entityClassName};
import ${project.packageName}.repository.${module.repositoryName};
import ${project.packageName}.service.${module.serviceName};
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class ${module.serviceImplName} implements ${module.serviceName} {

    private final ${module.repositoryName} repository;

    public ${module.serviceImplName}(${module.repositoryName} repository) {
        this.repository = repository;
    }

<#list module.endpoints as ep>
<#assign pathParams><#list ep.pathVariables as pv>${entity.idType} ${pv}<#sep>, </#sep></#list></#assign>
    @Override
    public ${ep.returnType} ${ep.methodName}(<#if ep.paged>Pageable pageable<#else>${pathParams}<#if ep.pathVariables?has_content && ep.hasRequestBody>, </#if><#if ep.hasRequestBody>${ep.requestBodyType} request</#if></#if>) {
<#if ep.crud && ep.httpMethod == "POST" && !ep.hasPathVariable>
        ${module.entityClassName} entity = new ${module.entityClassName}();
        applyValues(entity, request);
        return toResponse(repository.save(entity));
<#elseif ep.paged>
        return repository.findAll(pageable).map(this::toResponse);
<#elseif ep.crud && ep.httpMethod == "GET" && ep.hasPathVariable>
        return repository.findById(${ep.idVariable}).map(this::toResponse)
                .orElseThrow(() -> new NoSuchElementException("${module.entityClassName} not found: " + ${ep.idVariable}));
<#elseif ep.crud && ep.httpMethod == "PUT" && ep.hasPathVariable>
        ${module.entityClassName} entity = repository.findById(${ep.idVariable})
                .orElseThrow(() -> new NoSuchElementException("${module.entityClassName} not found: " + ${ep.idVariable}));
        applyValues(entity, request);
        return toResponse(repository.save(entity));
<#elseif ep.crud && ep.httpMethod == "DELETE">
        repository.deleteById(${ep.idVariable});
<#else>
        // TODO: implement ${ep.methodName} logic
        throw new UnsupportedOperationException("${ep.methodName} not yet implemented");
</#if>
    }

</#list>
    private void applyValues(${module.entityClassName} entity, Create${module.entityClassName}Dto request) {
<#list entity.fields as field>
<#-- Mirrors CreateDto's filter exactly; see BackendField.serverManaged. -->
<#if !field.serverManaged>
        entity.set${field.name?cap_first}(request.get${field.name?cap_first}());
</#if>
</#list>
    }

    private ${module.entityClassName}ResponseDto toResponse(${module.entityClassName} entity) {
        ${module.entityClassName}ResponseDto dto = new ${module.entityClassName}ResponseDto();
        dto.setId(entity.getId());
<#list entity.fields as field>
        dto.set${field.name?cap_first}(entity.get${field.name?cap_first}());
</#list>
<#if entity.audited>
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setLastModifiedAt(entity.getLastModifiedAt());
</#if>
        return dto;
    }
}
