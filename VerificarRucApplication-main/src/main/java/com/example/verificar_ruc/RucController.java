package com.example.verificar_ruc;

import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RucController {

    private final HttpClient client = HttpClient.newHttpClient();
    private final RedisTemplate<String, String> redisTemplate;

    public RucController(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 1. Verificar si el RUC existe en SRI
    @GetMapping("/verificarRuc")
    public boolean verificarRuc(@RequestParam String ruc) throws IOException, InterruptedException {
        if (ruc.length() == 10) ruc += "001";

        String url = "https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/existePorNumeroRuc?numeroRuc=" + ruc;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return Boolean.parseBoolean(response.body());
    }

    // 2. Obtener datos del contribuyente (con Redis cache)
    @GetMapping("/datosContribuyente")
    public String obtenerDatosContribuyente(@RequestParam String ruc) throws IOException, InterruptedException {
        if (ruc.length() == 10) ruc += "001";

        String redisKey = "contribuyente:" + ruc;

        // Buscar en Redis
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            return cached;
        }

        // Si no está en cache, hacer la consulta
        String url = "https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/obtenerPorNumerosRuc?ruc=" + ruc;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();

        // Guardar en cache (por 1 hora)
        redisTemplate.opsForValue().set(redisKey, body, java.time.Duration.ofHours(1));

        return body;
    }

    // 3. Obtener datos del vehículo por número de placa
    @GetMapping("/vehiculo")
    public String obtenerDatosVehiculo(@RequestParam String placa) throws IOException, InterruptedException {
        String url = "https://srienlinea.sri.gob.ec/sri-matriculacion-vehicular-recaudacion-servicio-internet/rest/BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn?numeroPlacaCampvCpn=" + placa;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    // 4. Obtener puntos de licencia desde ANT
    @GetMapping("/puntosLicencia")
    public String obtenerPuntos(@RequestParam String cedula, @RequestParam String placa) throws IOException, InterruptedException {
        String url = "https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp?ps_tipo_identificacion=CED&ps_identificacion=" + cedula + "&ps_placa=" + placa;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
}
