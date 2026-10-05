"""
Convierte el manual LEA (Excel) al catálogo que carga el core al arrancar.

    pip install openpyxl
    python scripts/lea_a_json.py ["00. Manual completo LEA ESSALUD (Y-O).xlsx"]

Escribe same_quarkus/src/main/resources/seed/lea.json. Cuando el manual cambie: regenerar, revisar
el diff y desplegar. El core solo lo carga si el catálogo está vacío (ver LeaCatalogInitializer);
para una BD ya cargada, los cambios se hacen por la API del catálogo.

Hoja "Criterios": una fila por criterio x condición específica. Reglas de conversión:
  - Niveles 0/1/2: vienen al final del texto del criterio ("0: ...; 1: ...; 2: ...").
  - Varias fuentes en una celda (una por línea); "Y/O" dice si se exigen todas (Y) o alguna (O).
  - "¿Aplica a todo ESSALUD?" = Sí con enlace en "Ubicación" -> fuente predefinida (no se sube);
    "Ubicación" = "No implementado" -> fuente no implementada; el resto la sube la IPRESS.
  - En condiciones de observación, entrevista o auditoría de HC la "fuente" dice qué verificar en
    campo: va a verificationGuide (esas condiciones no tienen fuentes que subir).
  - Peso y categoría (GER/PRE/APO) de cada macroproceso: modelo de calificación de acreditación
    (samec-old/recursos/modelo excel acredita.xlsx).
"""
import json
import re
import sys
from collections import OrderedDict
from pathlib import Path

import openpyxl

RAIZ = Path(__file__).resolve().parent.parent
EXCEL = Path(sys.argv[1]) if len(sys.argv) > 1 else RAIZ / "00. Manual completo LEA ESSALUD (Y-O).xlsx"
SALIDA = RAIZ / "same_quarkus" / "src" / "main" / "resources" / "seed" / "lea.json"

# código -> (categoría, peso % en el puntaje final)
MACROPROCESOS = {
    "DIR": ("MANAGEMENT", 8), "GRH": ("MANAGEMENT", 6), "GCA": ("MANAGEMENT", 7),
    "MRA": ("MANAGEMENT", 7), "GSD": ("MANAGEMENT", 2), "CGP": ("MANAGEMENT", 5),
    "ATA": ("CARE_PROVISION", 7), "AEX": ("CARE_PROVISION", 7), "ATH": ("CARE_PROVISION", 7),
    "EMG": ("CARE_PROVISION", 7), "ATQ": ("CARE_PROVISION", 7), "DIV": ("CARE_PROVISION", 5),
    "ADT": ("SUPPORT", 2.5), "ADA": ("SUPPORT", 2.5), "RCR": ("SUPPORT", 2.5), "GMD": ("SUPPORT", 2.5),
    "GIN": ("SUPPORT", 2.5), "DLDE": ("SUPPORT", 2.5), "MRS": ("SUPPORT", 2.5), "NYD": ("SUPPORT", 2.5),
    "GIM": ("SUPPORT", 2.5), "EIF": ("SUPPORT", 2.5),
}
TIPOS = {"Estructura": "STRUCTURE", "Proceso": "PROCESS", "Resultado": "RESULT"}
TECNICAS = {"Revisión de documento": "DOCUMENT_REVIEW", "Observación": "OBSERVATION",
            "Muestreo Auditoría de HC": "SAMPLING_MEDICAL_RECORD_AUDIT", "Entrevista": "INTERVIEW"}
LOGICA = {"Y": "ALL", "O": "ANY"}

# Columnas de la hoja "Criterios"
(N, MP, MACRO, COD_EST, ESTANDAR, COD_CRIT, TIPO, PUNTAJE, CRITERIO, PROCESO, SUBPROCESO, CATEGORIAS,
 CONDICION, APLICACION, NORMA, ENLACE_NORMA, FUNCION, RESPONSABLE, TECNICA, FUENTE, Y_O, TODO_ESSALUD,
 UBICACION) = range(23)

avisos = []


def texto(v):
    if v is None:
        return ""
    return re.sub(r"[ \t]+", " ", str(v).replace("\r", "")).strip()


def lineas(v):
    """Una celda con varios valores, uno por línea (con o sin guion inicial)."""
    return [l for l in (x.strip().lstrip("-•").strip() for x in texto(v).split("\n")) if l]


def urls(v):
    return [l for l in lineas(v) if l.lower().startswith("http")]


def o_none(v):
    t = texto(v)
    return t or None


def niveles(codigo, enunciado):
    """Separa el enunciado de los niveles "0: ...; 1: ...; 2: ..." que trae al final."""
    m = re.search(r"(?:^|\s)0\s*[:;]", enunciado)
    if not m:
        avisos.append(f"{codigo}: sin niveles 0/1/2 en el enunciado")
        return enunciado.strip(), []
    cuerpo, resto = enunciado[:m.start()].strip(), enunciado[m.start():]
    marcas = list(re.finditer(r"(?<!\d)([012])\s*[:;]\s*", resto))
    lista = []
    for i, marca in enumerate(marcas):
        fin = marcas[i + 1].start() if i + 1 < len(marcas) else len(resto)
        descripcion = resto[marca.end():fin].strip().rstrip(";.").strip()
        lista.append({"score": int(marca.group(1)), "description": descripcion})
    if [n["score"] for n in lista] != [0, 1, 2]:
        avisos.append(f"{codigo}: niveles {[n['score'] for n in lista]} (se cargan tal cual)")
    return cuerpo or enunciado.strip(), lista


def fuentes(fila):
    nombres = lineas(fila[FUENTE])
    ubicacion = texto(fila[UBICACION])
    todo_essalud = texto(fila[TODO_ESSALUD]) == "Sí"
    enlaces = urls(fila[UBICACION])
    if "no implementado" in ubicacion.lower():
        disponibilidad = "NOT_IMPLEMENTED"
    elif todo_essalud and enlaces:
        disponibilidad = "PREDEFINED"
    else:
        disponibilidad = "PROVIDED_BY_FACILITY"
        if todo_essalud:
            avisos.append(f"{fila[COD_CRIT]}: aplica a todo EsSalud pero sin enlace -> la sube la IPRESS")
    if enlaces and len(enlaces) != len(nombres) and len(enlaces) != 1:
        avisos.append(f"{fila[COD_CRIT]}: {len(nombres)} fuente(s) y {len(enlaces)} enlace(s); se asignan en orden")
    lista = []
    for i, nombre in enumerate(nombres):
        enlace = enlaces[min(i, len(enlaces) - 1)] if enlaces else None
        lista.append({"name": nombre[:1000], "referenceUrl": enlace, "availability": disponibilidad,
                      "appliesSystemWide": todo_essalud, "displayOrder": i + 1})
    return lista


def referencias(fila):
    titulos = lineas(fila[NORMA])
    enlaces = urls(fila[ENLACE_NORMA])
    if len(titulos) > 1 and len(enlaces) != len(titulos):
        avisos.append(f"{fila[COD_CRIT]}: {len(titulos)} normas y {len(enlaces)} enlace(s)"
                      + ("; se asignan en orden" if enlaces else "; quedan sin enlace"))
    return [{"title": t[:1000], "url": enlaces[min(i, len(enlaces) - 1)] if enlaces else None,
             "applicableExcerpt": o_none(fila[APLICACION])} for i, t in enumerate(titulos)]


def condicion(fila, orden):
    tecnica = TECNICAS[texto(fila[TECNICA]).replace("\n", " ")]
    documental = tecnica == "DOCUMENT_REVIEW"
    lista = fuentes(fila) if documental else []
    logica = LOGICA.get(texto(fila[Y_O]).upper())
    if documental and len(lista) > 1 and not logica:
        logica = "ALL"
        avisos.append(f"{fila[COD_CRIT]}: {len(lista)} fuentes sin Y/O -> se exigen todas (Y)")
    return {
        "description": texto(fila[CONDICION]),
        "scoreWeight": int(texto(fila[PUNTAJE])),
        "technique": tecnica,
        "process": texto(fila[PROCESO]) or None,
        "subprocess": texto(fila[SUBPROCESO]) or None,
        "facilityFunction": o_none(fila[FUNCION]),
        "responsibleRole": o_none(fila[RESPONSABLE]),
        "sourceLogic": logica if len(lista) > 1 else None,
        "verificationGuide": None if documental else ("\n".join(lineas(fila[FUENTE])) or None),
        "normativeReferences": referencias(fila),
        "sources": lista,
        "displayOrder": orden,
    }


def main():
    hoja = openpyxl.load_workbook(EXCEL, data_only=True)["Criterios"]
    filas = [list(f) for f in hoja.iter_rows(min_row=2, values_only=True) if any(v is not None for v in f)]

    macros = OrderedDict()
    for fila in filas:
        cod_est = texto(fila[COD_EST])
        cod_macro = cod_est.split("-")[0]
        categoria, peso = MACROPROCESOS[cod_macro]
        macro = macros.setdefault(cod_macro, {
            "code": cod_macro, "name": texto(fila[MACRO]), "category": categoria, "scoreWeight": peso,
            "displayOrder": int(texto(fila[MP])), "standards": OrderedDict()})
        estandar = macro["standards"].setdefault(cod_est, {
            "code": cod_est, "statement": texto(fila[ESTANDAR]), "criteria": OrderedDict()})
        cod_crit = texto(fila[COD_CRIT])
        if cod_crit not in estandar["criteria"]:
            enunciado, lista_niveles = niveles(cod_crit, str(fila[CRITERIO] or ""))
            estandar["criteria"][cod_crit] = {
                "code": cod_crit, "statement": texto(enunciado), "type": TIPOS[texto(fila[TIPO])],
                "categories": [c for c in texto(fila[CATEGORIAS]).split("/") if c],
                "scoreLevels": lista_niveles, "conditions": []}
        criterio = estandar["criteria"][cod_crit]
        criterio["conditions"].append(condicion(fila, len(criterio["conditions"]) + 1))

    resultado = {"source": EXCEL.name, "macroProcesses": []}
    for macro in macros.values():
        macro["standards"] = list(macro["standards"].values())
        for est in macro["standards"]:
            est["criteria"] = list(est["criteria"].values())
            for crit in est["criteria"]:
                total = sum(c["scoreWeight"] for c in crit["conditions"])
                if total != 2:
                    raise SystemExit(f"{crit['code']}: los pesos suman {total}, deben sumar 2")
        resultado["macroProcesses"].append(macro)

    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.write_text(json.dumps(resultado, ensure_ascii=False, indent=1), encoding="utf-8")
    estandares = [e for m in resultado["macroProcesses"] for e in m["standards"]]
    criterios = [c for e in estandares for c in e["criteria"]]
    print(f"{SALIDA.relative_to(RAIZ)}: {len(resultado['macroProcesses'])} macroprocesos, {len(estandares)} estándares, "
          f"{len(criterios)} criterios, {sum(len(c['conditions']) for c in criterios)} condiciones")
    print(f"Pesos: {sum(m['scoreWeight'] for m in resultado['macroProcesses'])} %")
    if avisos:
        print(f"Avisos ({len(avisos)}):")
        for a in avisos:
            print("  -", a)


if __name__ == "__main__":
    main()
