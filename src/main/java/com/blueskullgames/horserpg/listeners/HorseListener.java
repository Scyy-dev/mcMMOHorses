package com.blueskullgames.horserpg.listeners;

import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeSet;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Horse.Variant;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.HorseJumpEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import com.blueskullgames.horserpg.HorseRPG;
import com.blueskullgames.horserpg.RPGHorse;
import com.blueskullgames.horserpg.skills.Swiftness;
import com.blueskullgames.horserpg.utils.NewHorseVarientUtil;
import com.blueskullgames.horserpg.utils.ReflectionUtil;

@SuppressWarnings("deprecation")
public class HorseListener implements Listener {

	/**
	 * Horse inventory click event
	 */
	@EventHandler
	public void onInventoryClick(InventoryClickEvent e) {
		if (e.getInventory() instanceof HorseInventory)
			if (e.getSlot() == 0 && ((e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.SADDLE)
					|| (e.getCursor() != null && e.getCursor().getType() == Material.SADDLE))) {

				if (HorseRPG.useSaddles) {
					RPGHorse h = HorseRPG.getHorse((Entity) e.getInventory().getHolder());
					ItemStack newSaddlePlace = new ItemStack(Material.SADDLE);
					ItemMeta im = newSaddlePlace.getItemMeta();
					im.setDisplayName(HorseRPG.SADDLE_SUMMON.replaceAll("%name%",h.name));
					newSaddlePlace.setItemMeta(im);
					if(e.getWhoClicked().getInventory().firstEmpty()==-1){
						e.setCancelled(true);
						return;
					}
					e.getWhoClicked().getInventory().addItem(newSaddlePlace);
					e.setCancelled(true);
					e.getWhoClicked().closeInventory();
					h.banish();
					return;
				}

				for (Entry<Entity, RPGHorse> horse : HorseRPG.getRPGHorseEntrys()) {
					if (e.getInventory().equals(RPGHorse.attributeUtil.getInventory(horse.getKey()))) {
						horse.getValue().hasSaddle = (e.getCursor() != null && e.getCursor().getType() == Material.SADDLE);
						break;
					}
				}
			}
	}

	/**
	 * Horse Damage Event
	 **/
	@EventHandler
	public void onEntityDamage(EntityDamageEvent evt) {
		if (evt.getEntityType() == EntityType.HORSE) {
			Entity horse = evt.getEntity();
			RPGHorse h = HorseRPG.getHorse(horse);
			if (h == null)
				return;

			if (h.godmode) {
				evt.setCancelled(true);
				return;
			}

			if (HorseRPG.invinciblefreerangehorses)
				if (horse.getPassenger() == null || !horse.getPassenger().getName().equals(h.owners_name)) {
					evt.setCancelled(true);
					return;
				}

			double chance = Math.random();

			boolean someoneRiding = false;
			try {
				someoneRiding = !horse.getPassengers().isEmpty();
			} catch (Exception | Error e) {
				someoneRiding = horse.getPassenger() != null;

			}

			double dmg = evt.getDamage();
			if (someoneRiding) {
				Player p;
				try {
					p = (Player) horse.getPassengers().get(0);
				} catch (Exception | Error e) {
					p = (Player) horse.getPassenger();
				}

				switch (evt.getCause()) {
					case BLOCK_EXPLOSION:
					case ENTITY_ATTACK:
					case ENTITY_EXPLOSION:
					case FIRE:
					case FIRE_TICK:
					case LAVA:
					case LIGHTNING:
					case THORNS:
						if (h.wrath.refreshed && chance < h.wrath.infuriateChance) {
							HorseRPG.msg(p, "&c**INFURIATE**");
							h.wrath.infuriate();
						}
						break;
					case PROJECTILE:
						if (h.wrath.refreshed && chance < h.wrath.infuriateChance) {
							HorseRPG.msg(p, "&c**INFURIATE**");
							h.wrath.infuriate();
						} else if (chance < h.agility.dodgeChance) {
							HorseRPG.msg(p, "&e**DODGE**");
							evt.setDamage(dmg / 2);
						}
						break;

					case FALL:
						int xp = dmg > 10 ? 10 : (int) dmg;
						if (chance < h.agility.gracefulChance) {
							HorseRPG.msg(p, "&a**GRACEFUL ROLL**");
							xp += 8;
							evt.setDamage(dmg / 10);
						} else if (chance < h.agility.rollChance) {
							HorseRPG.msg(p, "&a**ROLL**");
							xp += 6;
							evt.setDamage(dmg / 2);
						}
						h.agility.addXP(xp, p);
					default:
				}

				if (evt.getCause() != DamageCause.FALL) {
					h.vitality.addXP(dmg > 8 ? 8 : (int) (dmg / 1.2), p);
					h.wrath.addXP(dmg > 6 ? 6 : (int) (dmg / 1.4), p);
				}
			}

			if (evt.getCause() != DamageCause.FALL
					&& !((LivingEntity) horse).hasPotionEffect(PotionEffectType.REGENERATION)) {
				if (chance < h.vitality.rapidChance && dmg > 1.5) {
					h.vitality.heal(true);
				} else if (chance < h.vitality.revitalizeChance && dmg > 1.5)
					h.vitality.heal(false);
			}
		}
	}

	/**
	 * Horse Death Event
	 **/
	@EventHandler
	public void onHorseDeath(EntityDeathEvent evt) {
		RPGHorse h = HorseRPG.removeHorseInstance(evt.getEntity());
		if (h == null)
			return;

		h.isDead = true;
		h.holderOverUUID = null;
		h.respawnTimer = HorseRPG.deathTimer;
		//h.spawned = false;

		Player owner = Bukkit.getPlayer(h.owners_name);

		if (owner != null) {
			if (HorseRPG.permanentDeath) {
				HorseRPG.ownedHorses.get(owner.getName()).remove(h);
				HorseRPG.msg(owner, "&b" + h.name + "&a died!");
			}
		}

		if (HorseRPG.permanentDeath) {
			if (HorseRPG.isRPGHorse(h.getHorse())) {
				// h.horse.remove();
				HorseRPG.removeHorseInstance(h.getHorse());
			}
			HorseRPG.horses.remove(h);
			HorseRPG.ownedHorses.get(h.owners_name).remove(h);
			RPGHorse horseCur = null;
			if (HorseRPG.ownedHorses.get(h.owners_name).size() > 0) {
				for (RPGHorse g : HorseRPG.ownedHorses.get(h.owners_name)) {
					if (g.getHorse() != null) {
						horseCur = g;
						break;
					}
				}
			}
			HorseRPG.pCurrentHorse.put(owner.getUniqueId(), horseCur);
			HorseRPG.h_config.removeHorse(h);
			HorseRPG.h_config.save();
			// Remove the horse from the list of all horses
		} else {
			evt.getDrops().clear();
			// If the horse will respawn, remove the saddle.
		}
	}

	/**
	 * Horse Jump Event
	 **/
	@EventHandler
	public void onHorseJump(HorseJumpEvent evt) {
		Optional<?> a = ReflectionUtil.getMethod(evt.getClass(), "getEntity");
		if (a == Optional.empty())
			return;
		Entity horse = (Entity) ReflectionUtil.invokeMethod(evt, "getEntity", new Class[0]);

		boolean versionCheck = false;
		try {
			versionCheck = !horse.getPassengers().isEmpty();
		} catch (Exception | Error e) {
			versionCheck = horse.getPassenger() != null;

		}
		if (versionCheck) {
			Player p;
			try {
				p = (Player) horse.getPassengers().get(0);
			} catch (Exception | Error e) {
				p = (Player) horse.getPassenger();
			}
			RPGHorse h = HorseRPG.pCurrentHorse.get(p.getUniqueId());
			if (h != null) {
				if (evt.getPower() < 0.45) {
					Swiftness swiftness = h.swiftness;
					if (!swiftness.refreshed)
						HorseRPG.msg(p,
								HorseRPG.TOO_TIRED.replace("%name%", h.name).replace("%cd%", swiftness.sprintCd + ""));// "&c"
						// +
						// h.name
						// +
						// "
						// is
						// too
						// tired
						// to
						// use
						// that
						// ability.
						// &e("
						// +
						// swiftness.sprintCd
						// +
						// "s)");

					else {
						HorseRPG.msg(p, "&a**SPRINTING**");
						h.swiftness.sprint();
					}
				}
				if (evt.getPower() >= 1.0)
					h.agility.addXP(1, p);
			}
		}
	}

	@EventHandler
	public void breed(EntityBreedEvent e) {
		if (NewHorseVarientUtil.isHorse(e.getEntity().getType())) {
			if (HorseRPG.disableBreeding)
				e.setCancelled(true);
			// Now, for custom breeding

			RPGHorse h1 = HorseRPG.getHorse(e.getMother());
			RPGHorse h2 = HorseRPG.getHorse(e.getFather());

			if (h1 != null && h2 != null) {
				if (h1.variant == Variant.DONKEY || h1.variant == Variant.MULE || h2.variant == Variant.DONKEY
						|| h2.variant == Variant.MULE) {

					stopBreeding(h1, h2, e);
					HorseRPG.msg(e.getBreeder(), HorseRPG.NO_BREED_HORSES + " Geldings cannot breed");
					HorseRPG.msg(e.getBreeder(),
							h1.name + " : "
									+ (h1.variant == Variant.MULE || h1.variant == Variant.DONKEY ? ChatColor.RED
									: ChatColor.GREEN)
									+ h1.variant.name());
					HorseRPG.msg(e.getBreeder(),
							h2.name + " : "
									+ (h2.variant == Variant.MULE || h2.variant == Variant.DONKEY ? ChatColor.RED
									: ChatColor.GREEN)
									+ h2.variant.name());
					return;
				}
				if (h1.isMale == h2.isMale) {
					stopBreeding(h1, h2, e);
					HorseRPG.msg(e.getBreeder(), HorseRPG.NO_BREED_HORSES + " Horses are the same sex!");
					HorseRPG.msg(e.getBreeder(), h1.name + " : " + (h1.isMale ? "Male" : "Female"));
					HorseRPG.msg(e.getBreeder(), h2.name + " : " + (h2.isMale ? "Male" : "Female"));
					return;
				}
				if (!h1.allowBreeding || h2.allowBreeding) {
					stopBreeding(h1, h2, e);
					HorseRPG.msg(e.getBreeder(),
							HorseRPG.NO_BREED_HORSES + " Atleast one of the horses is not allowed to breed");
					HorseRPG.msg(e.getBreeder(), h1.name + " : "
							+ (h1.allowBreeding ? ChatColor.GREEN + "Allowed" : ChatColor.RED + "Not-Allowed"));
					HorseRPG.msg(e.getBreeder(), h2.name + " : "
							+ (h2.allowBreeding ? ChatColor.GREEN + "Allowed" : ChatColor.RED + "Not-Allowed"));
				}
			}
			if ((h1 != null || h2 != null) && HorseRPG.claimBreeding) {
				RPGHorse mainTester = null;
				if (h1 == null || !h1.owners_name.equals(e.getBreeder().getName())) {
					if (h2 != null && h2.owners_name.equals(e.getBreeder().getName())) {
						mainTester = h2;
					}
				} else {
					mainTester = h1;
				}
				if (mainTester == null)
					return;

				RPGHorse h;
				try {
					h = new RPGHorse((Player) e.getBreeder(), (Horse) e.getEntity());
					h.setVariant(((Horse) e.getEntity()).getVariant());
				} catch (Error | Exception e33) {
					h = new RPGHorse((Player) e.getBreeder(), e.getEntity());
					h.setVariant(((org.bukkit.entity.AbstractHorse) e.getEntity()).getVariant());
				}

				try {
					if (h instanceof Donkey) {
						((Donkey) h).isCarryingChest();
					}
					if (h instanceof Mule) {
						((Mule) h).isCarryingChest();
					}
				} catch (Error | Exception e432) {
				}

				h.setHorse(e.getEntity());
				h.setName(h.name);

				double minSpeed = Math.min(RPGHorse.attributeUtil.getSpeed(e.getMother()),
						RPGHorse.attributeUtil.getSpeed(e.getFather()));
				double maxSpeed = Math.max(RPGHorse.attributeUtil.getSpeed(e.getMother()),
						RPGHorse.attributeUtil.getSpeed(e.getFather()));
				double minJ = Math.min(RPGHorse.attributeUtil.getJumpHeight(e.getMother()),
						RPGHorse.attributeUtil.getJumpHeight(e.getFather()));
				double maxJ = Math.max(RPGHorse.attributeUtil.getJumpHeight(e.getMother()),
						RPGHorse.attributeUtil.getJumpHeight(e.getFather()));

				double speed = Math.min(RPGHorse.maxSpeed, (Math.random() * (maxSpeed - minSpeed)) + minSpeed
						+ (Math.random() * (RPGHorse.maxSpeed - RPGHorse.minSpeed) / 10));
				double jump = Math.min(RPGHorse.maxJump, (Math.random() * (maxJ - minJ)) + minJ
						+ (Math.random() * (RPGHorse.maxJump - RPGHorse.minJump) / 10));

				RPGHorse.attributeUtil.setJumpHeight(e.getEntity(), speed);
				RPGHorse.attributeUtil.setJumpHeight(e.getEntity(), jump);

				// pCurrentHorse.put(e.getBreeder().getUniqueId(), h);
				HorseRPG.addSpawnedHorse(e.getEntity(), h);
				if (!HorseRPG.ownedHorses.containsKey(e.getBreeder().getName()))
					HorseRPG.ownedHorses.put(e.getBreeder().getName(), new TreeSet<RPGHorse>());
				HorseRPG.ownedHorses.get(e.getBreeder().getName()).add(h);
				HorseRPG.horses.add(h);

				HorseRPG.msg(e.getBreeder(), HorseRPG.FOALBREED.replaceAll("%name%", h.name));
			}
		}
	}

	private void stopBreeding(final RPGHorse h1, final RPGHorse h2, EntityBreedEvent e) {
		((Ageable) h1.getHorse()).setBreed(false);
		((Ageable) h2.getHorse()).setBreed(false);
		e.setCancelled(true);
		new BukkitRunnable() {
			@Override
			public void run() {
				((Ageable) h1.getHorse()).setBreed(true);
				((Ageable) h2.getHorse()).setBreed(true);
			}
		}.runTaskLater(HorseRPG.instance, 20 * 5);
	}

}
