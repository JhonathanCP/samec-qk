package com.essalud.samec.organizacion;

import java.util.List;

import com.essalud.samec.shared.error.RecursoNoEncontradoException;
import com.essalud.samec.shared.error.ReglaNegocioException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Catálogo de la estructura organizacional: MacroRegión → Red → IPRESS. */
@ApplicationScoped
public class OrganizacionService {

    @Inject MacroRegionRepository macroRegiones;
    @Inject RedRepository redes;
    @Inject IpressRepository ipress;

    public record MacroRegionDto(Long id, String codigo, String nombre, boolean activo) {
        static MacroRegionDto de(MacroRegion m) {
            return new MacroRegionDto(m.getId(), m.getCodigo(), m.getNombre(), Boolean.TRUE.equals(m.getActivo()));
        }
    }

    public record RedDto(Long id, String codigo, String nombre, String descripcion,
                         Long idMacroRegion, String macroRegionNombre, boolean activo) {
        static RedDto de(Red r) {
            MacroRegion m = r.getMacroRegion();
            return new RedDto(r.getId(), r.getCodigo(), r.getNombre(), r.getDescripcion(),
                    m != null ? m.getId() : null, m != null ? m.getNombre() : null, Boolean.TRUE.equals(r.getActivo()));
        }
    }

    public record IpressDto(Long id, String codigo, String nombre, Long idRed, String redNombre,
                            NivelComplejidad nivelComplejidad, boolean activo) {
        static IpressDto de(Ipress i) {
            return new IpressDto(i.getId(), i.getCodigo(), i.getNombre(), i.getRed().getId(), i.getRed().getNombre(),
                    i.getNivelComplejidad(), Boolean.TRUE.equals(i.getActivo()));
        }
    }

    public record RedRequest(@NotBlank(message = "Debe indicar el código.") @Size(max = 50) String codigo,
                             @NotBlank(message = "Debe indicar el nombre.") @Size(max = 200) String nombre,
                             @Size(max = 500) String descripcion,
                             Long idMacroRegion) {
    }

    public record IpressRequest(@NotBlank(message = "Debe indicar el código.") @Size(max = 50) String codigo,
                                @NotBlank(message = "Debe indicar el nombre.") @Size(max = 250) String nombre,
                                @NotNull(message = "Debe indicar la Red.") Long idRed,
                                NivelComplejidad nivelComplejidad) {
    }

    // ── Consulta ──

    @Transactional
    public List<MacroRegionDto> macroRegiones() {
        return macroRegiones.todas().stream().map(MacroRegionDto::de).toList();
    }

    @Transactional
    public List<RedDto> redes(boolean soloActivas) {
        return redes.listar(soloActivas).stream().map(RedDto::de).toList();
    }

    @Transactional
    public RedDto red(Long id) {
        return RedDto.de(buscarRed(id));
    }

    @Transactional
    public List<IpressDto> ipress(Long idRed, boolean soloActivas) {
        return ipress.listar(idRed, soloActivas).stream().map(IpressDto::de).toList();
    }

    @Transactional
    public IpressDto ipress(Long id) {
        return IpressDto.de(buscarIpress(id));
    }

    // ── Administración ──

    @Transactional
    public RedDto guardarRed(Long id, RedRequest req) {
        String codigo = req.codigo().trim();
        redes.porCodigo(codigo).filter(r -> !r.getId().equals(id)).ifPresent(r -> {
            throw new ReglaNegocioException("Ya existe una Red con el código " + codigo + ".");
        });
        Red red = id == null ? new Red() : buscarRed(id);
        red.setCodigo(codigo);
        red.setNombre(req.nombre().trim());
        red.setDescripcion(req.descripcion());
        red.setMacroRegion(req.idMacroRegion() == null ? null : macroRegiones.findByIdOptional(req.idMacroRegion())
                .orElseThrow(() -> new ReglaNegocioException("La MacroRegión indicada no existe.")));
        if (id == null) redes.persist(red);
        return RedDto.de(red);
    }

    @Transactional
    public RedDto cambiarEstadoRed(Long id, boolean activo) {
        Red red = buscarRed(id);
        red.setActivo(activo);
        return RedDto.de(red);
    }

    @Transactional
    public IpressDto guardarIpress(Long id, IpressRequest req) {
        String codigo = req.codigo().trim();
        ipress.porCodigo(codigo).filter(i -> !i.getId().equals(id)).ifPresent(i -> {
            throw new ReglaNegocioException("Ya existe una IPRESS con el código " + codigo + ".");
        });
        Ipress i = id == null ? new Ipress() : buscarIpress(id);
        i.setCodigo(codigo);
        i.setNombre(req.nombre().trim());
        i.setRed(redes.findByIdOptional(req.idRed())
                .orElseThrow(() -> new ReglaNegocioException("La Red indicada no existe.")));
        i.setNivelComplejidad(req.nivelComplejidad());
        if (id == null) ipress.persist(i);
        return IpressDto.de(i);
    }

    @Transactional
    public IpressDto cambiarEstadoIpress(Long id, boolean activo) {
        Ipress i = buscarIpress(id);
        i.setActivo(activo);
        return IpressDto.de(i);
    }

    private Red buscarRed(Long id) {
        return redes.findByIdOptional(id).orElseThrow(() -> RecursoNoEncontradoException.de("Red", id));
    }

    private Ipress buscarIpress(Long id) {
        return ipress.findByIdOptional(id).orElseThrow(() -> RecursoNoEncontradoException.de("IPRESS", id));
    }
}
