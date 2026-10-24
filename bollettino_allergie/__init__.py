"""Bollettino delle allergie: pollini, spore e qualità dell'aria per un luogo."""

from .api import ErroreAPI, Luogo, cerca_luogo, scarica_previsioni
from .bollettino import Bollettino, crea_bollettino
from .catalogo import ALLERGENI, cerca_allergeni

__all__ = ["ALLERGENI", "Bollettino", "ErroreAPI", "Luogo", "cerca_allergeni",
           "cerca_luogo", "crea_bollettino", "scarica_previsioni"]
