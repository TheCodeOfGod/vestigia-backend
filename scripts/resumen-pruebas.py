#!/usr/bin/env python3
"""Resume en Markdown los reportes XML de Maven (surefire y failsafe).

Uso, desde la raíz del repo y después de ./mvnw verify:

    python3 scripts/resumen-pruebas.py                # conteo y lista completa de pruebas
    python3 scripts/resumen-pruebas.py --solo-conteo  # una sola línea

Lee target/surefire-reports/TEST-*.xml (unitarias y @WebMvcTest) y target/failsafe-reports/TEST-*.xml (*IT).
El nombre de cada prueba es su @DisplayName: el pom configura los reportes para escribirlo.
Sale con código 1 si no hay reportes y con 2 si alguna prueba falló o dio error.
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
    for carpeta, tipo in CARPETAS:
        for archivo in sorted((target / carpeta).glob("TEST-*.xml")):
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
    return pruebas, archivos


def contar(pruebas):
    return {r: sum(1 for p in pruebas if p["resultado"] == r) for r in RESULTADOS}


def git(*args):
    try:
        return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "?"


def celda(texto):
    return texto.replace("|", "\\|")


def linea_conteo(pruebas, conteo):
    return (f"{len(pruebas)} pruebas, {conteo['fallo']} fallos, {conteo['error']} errores, "
            f"{conteo['omitida']} omitidas")


def markdown(pruebas, archivos, conteo):
    verde = conteo["fallo"] == 0 and conteo["error"] == 0
    fecha = datetime.datetime.fromtimestamp(max(a.stat().st_mtime for a in archivos)).strftime("%Y-%m-%d %H:%M")
    commit = git("rev-parse", "--short", "HEAD")
    if git("status", "--porcelain") not in ("", "?"):
        commit += " (con cambios sin commitear)"
    rama = git("rev-parse", "--abbrev-ref", "HEAD")
    unitarias = sum(1 for p in pruebas if p["tipo"] == "unitaria")

    lineas = [
        f"{'✅' if verde else '❌'} {linea_conteo(pruebas, conteo)}.",
        "",
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

    pruebas, archivos = leer_pruebas(Path(args.target))
    if not archivos:
        print(f"No hay reportes en {args.target}/surefire-reports ni {args.target}/failsafe-reports. "
              "Corre ./mvnw verify primero.", file=sys.stderr)
        return 1
    conteo = contar(pruebas)
    print(linea_conteo(pruebas, conteo) if args.solo_conteo else markdown(pruebas, archivos, conteo))
    return 0 if conteo["fallo"] == 0 and conteo["error"] == 0 else 2


if __name__ == "__main__":
    sys.exit(main())
