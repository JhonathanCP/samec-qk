package com.essalud.samec.usuarios;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.essalud.samec.organizacion.Ipress;
import com.essalud.samec.organizacion.Red;
import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "usuarios")
public class Usuario extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long id;

    @Column(name = "username", length = 80, nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    @Column(name = "apellido", length = 120, nullable = false)
    private String apellido;

    @Column(name = "email", length = 180, nullable = false, unique = true)
    private String email;

    /** Red propia (roles de alcance Red). Para roles de IPRESS la Red sale de la IPRESS. */
    @ManyToOne
    @JoinColumn(name = "id_red")
    private Red red;

    @ManyToOne
    @JoinColumn(name = "id_ipress")
    private Ipress ipress;

    /** Desactivada no puede iniciar sesión y sus tokens dejan de servir de inmediato. */
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_roles",
            joinColumns = @JoinColumn(name = "id_usuario"),
            inverseJoinColumns = @JoinColumn(name = "id_rol"))
    private Set<Rol> roles = new HashSet<>();

    // ── Autorregistro ──
    @Column(name = "autoregistrado", nullable = false)
    private Boolean autoregistrado = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "registro_estado", length = 20, nullable = false)
    private EstadoRegistro registroEstado = EstadoRegistro.APROBADO;

    @Column(name = "registro_comentario", length = 500)
    private String registroComentario;

    @Column(name = "registro_revisado_por")
    private Long registroRevisadoPor;

    /** Nombre del revisor tal como era al resolver (evita cargar otro usuario para mostrarlo). */
    @Column(name = "registro_revisado_por_nombre", length = 250)
    private String registroRevisadoPorNombre;

    @Column(name = "registro_revisado_at")
    private LocalDateTime registroRevisadoAt;

    /** Cuándo quedó pendiente la solicitud ACTUAL (una rechazada puede reenviarse sobre la misma fila). */
    @Column(name = "registro_solicitado_at")
    private LocalDateTime registroSolicitadoAt;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    public Set<String> codigosRol() {
        return roles.stream().map(Rol::getCodigo).collect(Collectors.toSet());
    }

    public boolean tieneRol(String codigo) {
        return roles.stream().anyMatch(r -> r.getCodigo().equals(codigo));
    }

    /** Red efectiva: la propia o la de su IPRESS. */
    public Red redEfectiva() {
        return red != null ? red : (ipress != null ? ipress.getRed() : null);
    }

    public String nombreCompleto() {
        String completo = ((nombre != null ? nombre : "") + " " + (apellido != null ? apellido : "")).trim();
        return completo.isEmpty() ? username : completo;
    }
}
