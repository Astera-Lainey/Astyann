package ${project.packageName}.controller;

import ${project.packageName}.dto.*;
import ${project.packageName}.service.${module.serviceName};
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

<#assign mappingAnnotations = {"POST":"PostMapping", "GET":"GetMapping", "PUT":"PutMapping", "PATCH":"PatchMapping", "DELETE":"DeleteMapping"}>
@RestController
@RequestMapping("${module.requestMapping}")
public class ${module.controllerName} {

    private final ${module.serviceName} service;

    public ${module.controllerName}(${module.serviceName} service) {
        this.service = service;
    }

<#list module.endpoints as ep>
<#if ep.roles?has_content>
    @PreAuthorize("hasAnyRole(<#list ep.roles as role>'${role}'<#sep>, </#list>)")
</#if>
    @${mappingAnnotations[ep.httpMethod]}<#if ep.path?has_content>("${ep.path}")</#if>
<#if ep.returnType == "void">
    public ResponseEntity<Void> ${ep.methodName}(<#if ep.hasPathVariable>@PathVariable UUID id<#if ep.hasRequestBody>, </#if></#if><#if ep.hasRequestBody>@Valid @RequestBody ${ep.requestBodyType} request</#if>) {
        service.${ep.methodName}(<#if ep.hasPathVariable>id</#if>);
        return ResponseEntity.noContent().build();
    }
<#else>
    public ResponseEntity<${ep.returnType}> ${ep.methodName}(<#if ep.hasPathVariable>@PathVariable UUID id<#if ep.hasRequestBody>, </#if></#if><#if ep.hasRequestBody>@Valid @RequestBody ${ep.requestBodyType} request</#if>) {
        return ResponseEntity.status(HttpStatus.<#if ep.httpMethod == "POST">CREATED<#else>OK</#if>)
                .body(service.${ep.methodName}(<#if ep.hasPathVariable>id<#if ep.hasRequestBody>, </#if></#if><#if ep.hasRequestBody>request</#if>));
    }
</#if>

</#list>
}
