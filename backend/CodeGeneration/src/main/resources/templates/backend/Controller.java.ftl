package ${project.packageName}.controller;

import ${project.packageName}.dto.*;
import ${project.packageName}.service.${module.serviceName};
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
<#-- Path variables are named by the PCSF (/{productId}), so they are rendered from the projection
     rather than assumed to be "id", and the declaration order is the parameter order. -->
<#assign pathParams><#list ep.pathVariables as pv>@PathVariable ${entity.idType} ${pv}<#sep>, </#sep></#list></#assign>
<#assign pathArgs><#list ep.pathVariables as pv>${pv}<#sep>, </#sep></#list></#assign>
<#if ep.returnType == "void">
    public ResponseEntity<Void> ${ep.methodName}(${pathParams}<#if ep.pathVariables?has_content && ep.hasRequestBody>, </#if><#if ep.hasRequestBody>@Valid @RequestBody ${ep.requestBodyType} request</#if>) {
        service.${ep.methodName}(${pathArgs}<#if ep.pathVariables?has_content && ep.hasRequestBody>, </#if><#if ep.hasRequestBody>request</#if>);
        return ResponseEntity.noContent().build();
    }
<#else>
    public ResponseEntity<${ep.returnType}> ${ep.methodName}(<#if ep.paged>@PageableDefault(size = 20) Pageable pageable<#else>${pathParams}<#if ep.pathVariables?has_content && ep.hasRequestBody>, </#if><#if ep.hasRequestBody>@Valid @RequestBody ${ep.requestBodyType} request</#if></#if>) {
        return ResponseEntity.status(HttpStatus.<#if ep.httpMethod == "POST">CREATED<#else>OK</#if>)
                .body(service.${ep.methodName}(<#if ep.paged>pageable<#else>${pathArgs}<#if ep.pathVariables?has_content && ep.hasRequestBody>, </#if><#if ep.hasRequestBody>request</#if></#if>));
    }
</#if>

</#list>
}
