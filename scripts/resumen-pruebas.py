#!/usr/bin/env python3
"""Resume en Markdown los reportes XML de Maven (surefire y failsafe).

Uso, desde la raíz del repo y después de ./mvnw clean verify (sin clean pueden quedar reportes de una corrida
anterior):

    python3 scripts/resumen-pruebas.py                # conteo y lista completa de pruebas
    python3 scripts/resumen-pruebas.py --solo-conteo  # una sola línea

Lee target/surefire-reports/TEST-*.xml (unitarias y @WebMvcTest) y target/failsafe-reports/TEST-*.xml (*IT).
El nombre de cada prueba es su @DisplayName: el pom configura los reportes para escribirlo.
Sale con código 1 si no hay reportes, con 2 si alguna prueba falló o dio error y con 3 si falta una de las dos
carpetas de reportes (por ejemplo, tras ./mvnw test no hay reportes de failsafe). En ese caso avisa y el
veredicto sale como ⚠️, porque el conteo está incompleto.
"""

import argparse
import datetime
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

CARPETAS = (("surefire-reports", "unitaria"), ("failsafe-reports", "integración"))
RESULTADOS = {"paso": "✅ pasó", "fallo": "❌ falló", "error": "❌ error", "omitida": "⏭️ omitida"}


def leer_pruebas(target):
    pruebas = []
    archivos = []
    faltantes = []
    for carpeta, tipo in CARPETAS:
        reportes = sorted((target / carpeta).glob("TEST-*.xml"))
        if not reportes:
            faltantes.append(carpeta)
        for archivo in reportes:
            archivos.append(archivo)
            for caso in ET.parse(archivo).getroot().iter("testcase"):
                if caso.find("failure") is not None:
                    resultado = "fallo"
                elif caso.find("error") is not None:
                    resultado = "error"
                elif caso.find("skipped") is not None:
                    resultado = "omitida"
                else:
                    resultado = "paso"
                pruebas.append({
                    "tipo": tipo,
                    "clase": caso.get("classname", "").removeprefix("com.vestigia."),
                    "nombre": caso.get("name", ""),
                    "tiempo": float(caso.get("time") or 0),
                    "resultado": resultado,
                })
    return pruebas, archivos, faltantes


def contar(pruebas):
    return {r: sum(1 for p in pruebas if p["resultado"] == r) for r in RESULTADOS}


def git(repo, *args):
    """Corre git en el repo dueño de la carpeta target, no en el directorio actual."""
    try:
        return subprocess.run(["git", "-C", str(repo), *args], capture_output=True, text=True,
                              check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "?"


def celda(texto):
    return texto.replace("|", "\\|")


def linea_conteo(pruebas, conteo):
    return (f"{len(pruebas)} pruebas, {conteo['fallo']} fallos, {conteo['error']} errores, "
            f"{conteo['omitida']} omitidas")


def markdown(pruebas, archivos, conteo, repo, faltantes):
    verde = conteo["fallo"] == 0 and conteo["error"] == 0
    icono = ("✅" if not faltantes else "⚠️") if verde else "❌"
    fecha = datetime.datetime.fromtimestamp(max(a.stat().st_mtime for a in archivos)).strftime("%Y-%m-%d %H:%M")
    commit = git(repo, "rev-parse", "--short", "HEAD")
    if git(repo, "status", "--porcelain") not in ("", "?"):
        commit += " (con cambios sin commitear)"
    rama = git(repo, "rev-parse", "--abbrev-ref", "HEAD")
    unitarias = sum(1 for p in pruebas if p["tipo"] == "unitaria")

    lineas = [
        f"{icono} {linea_conteo(pruebas, conteo)}.",
        "",
        *[f"⚠️ No hay reportes en `target/{carpeta}`: el conteo está incompleto." for carpeta in faltantes],
        *([""] if faltantes else []),
        "| Total | Unitarias | Integración | Fallos | Errores | Omitidas | Fecha | Commit | Rama |",
        "|---:|---:|---:|---:|---:|---:|---|---|---|",
        f"| {len(pruebas)} | {unitarias} | {len(pruebas) - unitarias} | {conteo['fallo']} | {conteo['error']} "
        f"| {conteo['omitida']} | {fecha} | `{commit}` | `{rama}` |",
        "",
        "### Lista completa de pruebas",
        "",
        "| Tipo | Clase | Prueba | Tiempo (s) | Resultado |",
        "|---|---|---|---:|---|",
    ]
    for p in pruebas:
        lineas.append(f"| {p['tipo']} | `{p['clase']}` | {celda(p['nombre'])} | {p['tiempo']:.3f} "
                      f"| {RESULTADOS[p['resultado']]} |")
    return "\n".join(lineas)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--solo-conteo", action="store_true", help="imprime solo una línea con el conteo")
    parser.add_argument("--target", default="target", help="carpeta target de Maven (por defecto: target)")
    args = parser.parse_args()

    target = Path(args.target).resolve()
    pruebas, archivos, faltantes = leer_pruebas(target)
    if not archivos:
        print(f"No hay reportes en {args.target}/surefire-reports ni {args.target}/failsafe-reports. "
              "Corre ./mvnw clean verify primero.", file=sys.stderr)
        return 1
    for carpeta in faltantes:
        print(f"Aviso: no hay reportes en {target / carpeta}; el conteo está incompleto. "
              "Corre ./mvnw clean verify.", file=sys.stderr)
    conteo = contar(pruebas)
    if args.solo_conteo:
        linea = linea_conteo(pruebas, conteo)
        print(linea + (f" (faltan reportes de {', '.join(faltantes)})" if faltantes else ""))
    else:
        print(markdown(pruebas, archivos, conteo, target.parent, faltantes))
    if conteo["fallo"] or conteo["error"]:
        return 2
    return 3 if faltantes else 0


if __name__ == "__main__":
    sys.exit(main())
