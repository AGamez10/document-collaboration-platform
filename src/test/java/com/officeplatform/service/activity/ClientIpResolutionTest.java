package com.officeplatform.service.activity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La IP que queda escrita en la bitacora.
 *
 * <p>Con un proxy inverso adelante —lo habitual en una red corporativa— todas las peticiones
 * llegan con la direccion del proxy o del gateway de Docker. La bitacora terminaba anotando
 * {@code 172.18.0.1} en cada fila: tecnicamente cierto y completamente inutil para una auditoria,
 * que existe para responder desde que maquina se hizo algo.
 */
class ClientIpResolutionTest {

    @Test
    @DisplayName("the IPv6 localhost is written the same way as the IPv4 one")
    void normalisesTheIpv6Localhost() {
        // Son la misma maquina; ver dos formas distintas de lo mismo en el listado hace dudar de
        // lo que uno esta mirando.
        assertThat(ActivityLogRecorder.normalizeIp("0:0:0:0:0:0:0:1")).isEqualTo("127.0.0.1");
        assertThat(ActivityLogRecorder.normalizeIp("::1")).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("a real address is left exactly as it arrived")
    void leavesARealAddressAlone() {
        assertThat(ActivityLogRecorder.normalizeIp("192.168.1.50")).isEqualTo("192.168.1.50");
        assertThat(ActivityLogRecorder.normalizeIp("  10.0.0.7  ")).isEqualTo("10.0.0.7");
        assertThat(ActivityLogRecorder.normalizeIp("2001:db8::8a2e:370:7334"))
                .isEqualTo("2001:db8::8a2e:370:7334");
    }

    @Test
    @DisplayName("an absent address stays absent instead of becoming an empty string")
    void keepsAnAbsentAddressAbsent() {
        // Una cadena vacia en la columna se lee como "hubo una IP y era esto"; null dice la
        // verdad, que no se pudo determinar.
        assertThat(ActivityLogRecorder.normalizeIp(null)).isNull();
        assertThat(ActivityLogRecorder.normalizeIp("   ")).isNull();
    }
}
